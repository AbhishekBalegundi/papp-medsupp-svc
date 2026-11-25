package com.anthem.cartman.letterhistory.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.LetterRow;

/**
 * Module 13 — the two tables the Letter History Deleter reads and writes.
 *
 * <p>Handled with {@code JdbcTemplate} rather than through JPA, for the same reason
 * {@link com.anthem.cartman.appdata.repository.DependentRecordRepository} is: {@code TM_Record_History}
 * has <b>no primary key and no index at all</b>, so it cannot be an entity. {@code TM_Letter_History}
 * has one, but the only thing this module does to it is delete a row by id, and mapping it would mean
 * mapping an {@code ntext} letter body that nothing here ever reads.
 *
 * <p>A80 — the Delphi assembles all three statements by joining strings, one of them a {@code Delete}.
 * Every value here is bound.
 *
 * <p>No {@code nolock} hint, in line with the rest of the application; the Delphi puts one on the read
 * and a {@code rowlock} on both deletes.
 */
@Repository
public class LetterHistoryRepository {

    /**
     * US-13.5.2 — the Delphi's own query, bound.
     *
     * <p>{@code RHist_ID} is {@code char(15)}, so the stored value is space-padded and
     * {@code = '3465'} matches it without help. {@code rtrim} is applied to the column all the same,
     * which is what {@code DependentRecordRepository} does, so a key typed with a trailing space still
     * finds its letters.
     */
    private static final String LETTERS_SQL = """
            select RHist_Date, RHist_Letter_Name, RHist_Letter_ID
              from TM_Record_History
             where rtrim(RHist_ID) = ? and RHist_Event = 2
             order by RHist_Date
            """;

    /** US-13.7.2 — the letter itself. {@code LH_ID} is an int identity; the Delphi passes it as text. */
    private static final String DELETE_LETTER_SQL =
            "delete from TM_Letter_History where LH_ID = ?";

    /**
     * US-13.7.3 — the history event.
     *
     * <p>A83: keyed on the id, the event code and the letter id rather than on one row, because the
     * table has no key to address a row by. Whatever it matches, the count comes back.
     */
    private static final String DELETE_EVENT_SQL = """
            delete from TM_Record_History
             where rtrim(RHist_ID) = ? and RHist_Event = 2 and RHist_Letter_ID = ?
            """;

    private final JdbcTemplate jdbc;

    public LetterHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The event-2 letters for one key, oldest first. */
    public List<LetterRow> findLetters(String keyValue) {
        return jdbc.query(LETTERS_SQL, (rs, row) -> new LetterRow(
                rs.getTimestamp("RHist_Date") == null
                        ? null
                        : rs.getTimestamp("RHist_Date").toLocalDateTime(),
                trimmed(rs.getString("RHist_Letter_Name")),
                rs.getInt("RHist_Letter_ID")), keyValue);
    }

    public int deleteLetter(int letterId) {
        return jdbc.update(DELETE_LETTER_SQL, letterId);
    }

    public int deleteEvent(String keyValue, int letterId) {
        return jdbc.update(DELETE_EVENT_SQL, keyValue, letterId);
    }

    /** {@code char(64)} pads what it stores; the padding is the column's, not the letter's name. */
    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
