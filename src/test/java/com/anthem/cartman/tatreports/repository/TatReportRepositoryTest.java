package com.anthem.cartman.tatreports.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.tatreports.dto.TatViews.Pend;
import com.anthem.cartman.tatreports.dto.TatViews.ReportRow;

/**
 * Module 10 — the report statements and the row mapping.
 *
 * <p>The two report statements cannot be run here: they use `STRING_AGG`, a full outer join, bracketed
 * aliases, `with (nolock)` and `option (maxdop 2)`, none of which the test database accepts. They are
 * asserted for the things that were got wrong in the Delphi and that a change could silently undo — the
 * date range being bound rather than pasted, and the hardcoded application id being gone. The pend query
 * and the row mapping, which is where the logic lives, are exercised properly.
 */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class TatReportRepositoryTest {

    @Autowired
    private TatReportRepository repository;

    @Nested
    @DisplayName("US-10.6.6 — reading the pends")
    class Pends {

        @Test
        @DisplayName("returns the pends for the applications asked for, and only those")
        void forApplications() {
            List<Pend> pends = repository.pends(List.of(3458));

            assertTrue(pends.stream().allMatch(p -> p.applicationId() == 3458),
                    "nothing from an application that was not asked for");
            // 9010 and 9011 are pends; 9012 is a task and 9013 an approval, both excluded.
            assertEquals(2, pends.size());
            assertEquals(LocalDate.of(2026, 2, 2), pends.get(0).start(), "ordered by start");
            assertEquals(LocalDate.of(2026, 2, 6), pends.get(0).close());
            assertNull(pends.get(1).close(), "still open");
        }

        @Test
        @DisplayName("a task is not a pend, whatever its dates")
        void tasksExcluded() {
            assertTrue(repository.pends(List.of(3458)).stream()
                    .noneMatch(p -> LocalDate.of(2026, 2, 3).equals(p.start())),
                    "9012 is UW_ActionType 'task'");
        }

        @Test
        @DisplayName("and neither is an approval or a denial")
        void approvalsExcluded() {
            assertTrue(repository.pends(List.of(3458)).stream()
                    .noneMatch(p -> LocalDate.of(2026, 2, 4).equals(p.start())),
                    "9013 is 'Approved'");
        }

        @Test
        @DisplayName("more than one application at a time")
        void several() {
            assertEquals(4, repository.pends(List.of(3455, 3458)).size(), "2 from each");
        }

        @Test
        @DisplayName("and nothing at all for an empty list, without going to the database")
        void noApplications() {
            assertEquals(List.of(), repository.pends(List.of()));
        }

        @Test
        @DisplayName("an application with no actions contributes none")
        void unknownApplication() {
            assertEquals(List.of(), repository.pends(List.of(999999)));
        }
    }

    @Nested
    @DisplayName("US-10.4 / US-10.5 — what the statements must keep saying")
    class Statements {

        private String sql(String field) throws Exception {
            var f = TatReportRepository.class.getDeclaredField(field);
            f.setAccessible(true);
            return (String) f.get(null);
        }

        @Test
        @DisplayName("A61 — both bind the date range rather than pasting it in")
        void datesAreBound() throws Exception {
            for (String field : List.of("GENERAL_SQL", "AGENT_SQL")) {
                String text = sql(field);
                assertTrue(text.contains("App_Keying_Dt >= ?"), field);
                assertTrue(text.contains("App_Keying_Dt <= ?"), field);
            }
        }

        @Test
        @DisplayName("A53 — and the Agent statement carries no hardcoded application id")
        void noHardcodedId() throws Exception {
            assertTrue(sql("AGENT_SQL").contains("RHist_Field = 456"), "the transfer field is kept");
            assertTrue(!sql("AGENT_SQL").contains("4499019"),
                    "the debugging filter that returned an empty file is gone");
        }

        @Test
        @DisplayName("the restrictions and hints the Delphi relies on are kept")
        void keptFromTheDelphi() throws Exception {
            String general = sql("GENERAL_SQL");
            assertTrue(general.contains("App_Typ in ('SUP','SEL')"));
            assertTrue(general.contains("with (nolock)"));
            assertTrue(general.contains("option (maxdop 2)"));
            assertTrue(general.contains("STRING_AGG(icd_code, ',')"));
        }
    }

    @Nested
    @DisplayName("the row mapping")
    class Mapping {

        /** A stub result set: the visible columns as text, then the four carried dates. */
        private ResultSet resultSet(int visible, String transfer) throws SQLException {
            ResultSet rs = Mockito.mock(ResultSet.class);
            Mockito.when(rs.getInt(1)).thenReturn(4603);
            for (int i = 1; i <= visible; i++) {
                Mockito.when(rs.getString(i)).thenReturn(i == 22 ? transfer : "  value" + i + "  ");
            }
            Mockito.when(rs.getDate(visible + 1)).thenReturn(Date.valueOf("2026-02-08"));
            Mockito.when(rs.getDate(visible + 2)).thenReturn(Date.valueOf("2026-02-09"));
            Mockito.when(rs.getDate(visible + 3)).thenReturn(null);
            Mockito.when(rs.getDate(visible + 4)).thenReturn(Date.valueOf("2026-02-07"));
            return rs;
        }

        @Test
        @DisplayName("trims every value, as the Delphi trims on the way out")
        void trims() throws SQLException {
            ReportRow row = TatReportRepository.row(resultSet(26, null), 26, false);

            assertEquals(26, row.values().size());
            assertEquals("value1", row.values().get(0));
            assertEquals(4603, row.applicationId());
        }

        @Test
        @DisplayName("carries the four dates the calculation needs")
        void carriesDates() throws SQLException {
            ReportRow row = TatReportRepository.row(resultSet(26, null), 26, false);

            assertEquals(LocalDate.of(2026, 2, 8), row.keyingDate());
            assertEquals(LocalDate.of(2026, 2, 9), row.keyingSentDate());
            assertNull(row.completeDate());
            assertEquals(LocalDate.of(2026, 2, 7), row.applicationDate());
        }

        @Test
        @DisplayName("a null value becomes an empty cell rather than the word null")
        void nulls() throws SQLException {
            ResultSet rs = resultSet(26, null);
            Mockito.when(rs.getString(3)).thenReturn(null);

            assertEquals("", TatReportRepository.row(rs, 26, false).values().get(2));
        }

        @Test
        @DisplayName("US-10.5.4 — an Agent row reads its transfer date from the line")
        void agentTransferDate() throws SQLException {
            ReportRow row = TatReportRepository.row(resultSet(27, "03/24/2026"), 27, true);

            assertEquals(LocalDate.of(2026, 3, 24), row.transferDate());
        }

        @Test
        @DisplayName("and a General row has none")
        void generalHasNoTransferDate() throws SQLException {
            assertNull(TatReportRepository.row(resultSet(26, null), 26, false).transferDate());
        }
    }

    @Nested
    @DisplayName("reading convert(char(10), RHist_Date, 101)")
    class Parsing {

        @Test
        @DisplayName("reads mm/dd/yyyy")
        void reads() {
            assertEquals(LocalDate.of(2026, 3, 24), TatReportRepository.parse("03/24/2026"));
            assertEquals(LocalDate.of(2026, 3, 24), TatReportRepository.parse("  03/24/2026  "));
        }

        @Test
        @DisplayName("and treats anything it cannot read as absent rather than failing the report")
        void refuses() {
            assertNull(TatReportRepository.parse(null));
            assertNull(TatReportRepository.parse(""));
            assertNull(TatReportRepository.parse("   "));
            assertNull(TatReportRepository.parse("2026-03-24"), "not the format the statement gives");
            assertNull(TatReportRepository.parse("13/45/2026"), "no such date");
            assertNull(TatReportRepository.parse("ab/cd/efgh"));
        }
    }
}
