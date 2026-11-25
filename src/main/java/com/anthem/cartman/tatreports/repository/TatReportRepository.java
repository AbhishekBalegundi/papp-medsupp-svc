package com.anthem.cartman.tatreports.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.anthem.cartman.tatreports.dto.TatViews.Pend;
import com.anthem.cartman.tatreports.dto.TatViews.ReportRow;

/**
 * Module 10 — the two report statements, transcribed from `GetReportSQL` and
 * `GetAgentDetailReportSQL` ({@code frmTATReport.pas} lines 750-849 and 677-744).
 *
 * <p>Read through {@code JdbcTemplate} rather than a mapped entity, as the keyless tables elsewhere in
 * this codebase are: the statements group, aggregate with {@code STRING_AGG} and outer join, none of
 * which a JPQL query expresses, and the result is a report row rather than a record anyone saves. No
 * stored procedure is involved.
 *
 * <p>Kept from CARTMAN exactly: the `(nolock)` hint, the join conditions, the two region CASE lists,
 * the Pend Source and Clean App expressions, the `App_Typ in ('SUP','SEL')` restriction, the grouping,
 * the ordering and `Option (MAXDOP 2)`.
 *
 * <p>Changed, each recorded in the requirements:
 * <ul>
 *   <li><b>A61</b> — the dates are bound, not pasted into the statement text.</li>
 *   <li><b>A53</b> — the Agent statement's `and App_ID = 4499019` is gone. It pinned the whole report
 *       to one application id left behind from debugging, and that id is not in this database, so the
 *       report returned an empty file. Without it the same range returns four rows.</li>
 *   <li><b>A62</b> — the seven `{_...}` placeholder columns are not selected. CARTMAN selects them as
 *       literal text and then does a string replace on the finished CSV line; here the computed figures
 *       are appended as values, so nothing has to be pattern-matched out of assembled text.</li>
 * </ul>
 */
@Repository
public class TatReportRepository {

    private final JdbcTemplate jdbc;

    public TatReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The columns both reports share, in the order the header names them. */
    private static final String SHARED_COLUMNS = """
                App_Source as [App Source],
                App_Keying_Dt as [App Rcvd Date],
                App_DCN as [DCN],
                App_Process as [GI],
                App_Smoker as [Tobacco_Ind],
                App_Eft_Dt as [Effecitve Date],
                App_Product_State as [State],
                case
                    when rtrim(App_Product_State) in ('CA','CO','NV','AZ','TX') then 'West'
                    when rtrim(App_Product_State) in ('MO','WI','IN','OH','KY') then 'Central'
                    when rtrim(App_Product_State) in ('ME','NH','NY','CT','VA','GA') then 'East'
                    else 'Unknown'
                end as [Sales Region],
                case
                    when rtrim(App_Product_State) in ('CA','CO','GA','NV','VA','WI') then 'West Team'
                    when rtrim(App_Product_State) in ('NY','CT','ME','NH','KY','OH','IN','MO','TX','AZ')
                        then 'Central Team'
                    else 'Unknown'
                end as [Ops Region],
                App_Typ as [Product],
                App_Plan as [Plan],
                App_Lnm as [Last Name],
                App_Fnm as [First Name],
                App_DOB as [DOB],
                App_HCID as [HCID],
                App_Agt_Num as [Agent Num],
                App_Med_Num as [Medicare Number],
                App_UW_Status as [Final Status],
                Max(App_UWStatus_Dt) as [Status Dt]
            """;

    private static final String PEND_SOURCE_AND_CLEAN = """
                case
                    when UW_ActionSource = 'UW' then 'UW'
                    when UW_ActionSource = 'Enroll' then 'RFI'
                    else ' '
                end as [Pend Source],
                case (select count(UW_Action) from TM_UW_Actions
                      where App_ID = UW_App_ID
                        and UW_Action not like 'Appr%'
                        and UW_Action not like 'Den%'
                        and UW_Action not like 'Clos%')
                    when 0 then 'Yes'
                    else 'No'
                end as [Clean App],
                App_Enroll_Complete_Dt as [Enrolled Date],
                STRING_AGG(icd_code, ',') as [Diag Codes],
                '' as [Decline Reason]
            """;

    /** The dates the carve-out needs, carried on every row as CARTMAN's Agent statement carries them. */
    private static final String CARRIED_DATES =
            " App_Keying_Dt, App_KeyingSnt_Dt, App_Complete_Dt, App_Dt ";

    private static final String GROUP_TAIL = """
                App_Source, App_Keying_Dt, App_DCN, App_Process, App_Smoker, App_Eft_Dt,
                App_Product_State, App_Typ, App_Plan, App_Lnm, App_Fnm, App_DOB, App_HCID,
                App_Agt_Num, App_Med_Num, App_UW_Status, App_UWStatus_Dt, UW_ActionSource,
                App_Enroll_Complete_Dt, App_KeyingSnt_Dt, App_Complete_Dt, App_Dt
            """;

    /** US-10.4 — one row per application. Assembled once: the text never varies. */
    private static final String GENERAL_SQL =
            "select App_ID as [App ID], App_Sub_Typ as [App Type],\n"
                + SHARED_COLUMNS + ",\n" + PEND_SOURCE_AND_CLEAN + ",\n" + CARRIED_DATES
                + """
                from TM_Application with (nolock)
                    left join TM_UW_Actions on (App_ID = UW_App_ID) and (App_UW_Status = UW_Action)
                    left join TM_ICD9 on App_ID = icd_app_id
                where App_Typ in ('SUP','SEL')
                  and App_Keying_Dt >= ?
                  and App_Keying_Dt <= ?
                group by App_ID, App_Sub_Typ,
                """
                + GROUP_TAIL
                + " order by App_Keying_Dt, App_Sub_Typ option (maxdop 2)";

    /** US-10.5 — one row per underwriter transfer. */
    private static final String AGENT_SQL =
            "select App_ID as [App ID], RHist_New_Value as [Underwriter],\n"
                + SHARED_COLUMNS + ",\n"
                + " convert(char(10), RHist_Date, 101) as [TransferDt],\n"
                + PEND_SOURCE_AND_CLEAN + ",\n" + CARRIED_DATES
                + """
                from TM_Application with (nolock)
                    left join TM_UW_Actions on (App_ID = UW_App_ID) and (App_UW_Status = UW_Action)
                    left join TM_ICD9 on App_ID = icd_app_id
                    full outer join TM_Record_History on (RHist_ID = App_ID) and (RHist_Field = 456)
                where App_Typ in ('SUP','SEL')
                  and App_Keying_Dt >= ?
                  and App_Keying_Dt <= ?
                  and RHist_Date is not null
                group by App_ID, RHist_New_Value,
                """
                + GROUP_TAIL + ", RHist_Date "
                + " order by App_ID, RHist_Date, App_Keying_Dt, App_Sub_Typ option (maxdop 2)";

    public List<ReportRow> general(LocalDate from, LocalDate to) {
        return jdbc.query(GENERAL_SQL, (rs, n) -> row(rs, 26, false),
                Date.valueOf(from), Date.valueOf(to));
    }

    public List<ReportRow> agentDetail(LocalDate from, LocalDate to) {
        return jdbc.query(AGENT_SQL, (rs, n) -> row(rs, 27, true),
                Date.valueOf(from), Date.valueOf(to));
    }

    /**
     * US-10.6.6 — every pend that could bear on a carve-out, for the applications on the report.
     *
     * <p>One query for the whole run rather than CARTMAN's two per row: the window filter differs per
     * area, so it is applied per row in {@link com.anthem.cartman.tatreports.service.CarveOutCalculator},
     * which reaches the same answer without a round trip for every area of every application.
     */
    public List<Pend> pends(List<Integer> applicationIds) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", applicationIds.stream().map(id -> "?").toList());
        // No `nolock` hint, in line with the rest of the application: the Delphi puts one on every
        // read, and dropping it is what lets this query run against the test database as well.
        String sql = "select UW_App_ID, UW_Start, UW_Close from TM_UW_Actions"
                + " where UW_ActionType <> 'task'"
                + "   and UW_Action not like '%Approv%'"
                + "   and UW_Action not like '%Denied%'"
                + "   and UW_App_ID in (" + marks + ")"
                + " order by UW_App_ID, UW_Start";
        return jdbc.query(sql, (rs, n) -> new Pend(rs.getInt(1), date(rs, 2), date(rs, 3)),
                applicationIds.toArray());
    }

    /** The visible columns, then the carried dates the calculation needs. */
    static ReportRow row(ResultSet rs, int visible, boolean agent) throws SQLException {
        List<String> values = new ArrayList<>(visible);
        for (int i = 1; i <= visible; i++) {
            String value = rs.getString(i);
            values.add(value == null ? "" : value.trim());
        }
        int at = visible + 1;
        LocalDate keying = date(rs, at);
        LocalDate keyingSent = date(rs, at + 1);
        LocalDate complete = date(rs, at + 2);
        LocalDate applied = date(rs, at + 3);
        // US-10.5.4 — the Agent rows measure underwriting from the transfer date on the line, which the
        // statement puts immediately after Status Dt.
        LocalDate transfer = agent ? parse(values.get(21)) : null;
        return new ReportRow(rs.getInt(1), values, keying, applied, keyingSent, complete, transfer);
    }

    private static LocalDate date(ResultSet rs, int column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    /** `convert(char(10), RHist_Date, 101)` is mm/dd/yyyy. */
    static LocalDate parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.trim().split("/");
        if (parts.length != 3) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]));
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }
}
