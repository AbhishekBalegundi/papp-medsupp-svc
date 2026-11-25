package com.anthem.cartman.tatreports.service;

import java.util.List;

/**
 * US-10.8 — the CSV writer, one for both reports.
 *
 * <p><b>A57.</b> CARTMAN has two different rules: the General report quotes a field holding a comma,
 * the Agent report quotes a field holding a double quote, and neither doubles an embedded quote. So the
 * Agent report breaks its own columns on any address or plan name containing a comma, and both emit
 * unparseable output for a value containing a quote. This quotes when the value holds a comma, a quote,
 * a carriage return or a line feed, and doubles embedded quotes — RFC 4180, which is what a spreadsheet
 * expects.
 */
final class CsvWriter {

    private static final char[] MUST_QUOTE = { ',', '"', '\r', '\n' };

    private CsvWriter() {
    }

    /** One line, terminated with CRLF as RFC 4180 specifies. */
    static void appendLine(StringBuilder out, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(fields.get(i)));
        }
        out.append("\r\n");
    }

    static String escape(String raw) {
        // The Delphi trims every field on the way out; kept.
        String value = raw == null ? "" : raw.trim();
        if (!needsQuoting(value)) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static boolean needsQuoting(String value) {
        for (char c : MUST_QUOTE) {
            if (value.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }
}
