package com.anthem.cartman.zipcodes.dto;

/** Module 14 — Zipcode Editor. What the two tabs read and write. */
public final class ZipCodeViews {

    private ZipCodeViews() {
    }

    /**
     * One row of `asgZipData`: State, County, City, Choice and Zip Id.
     *
     * <p>{@code choice} is the raw {@code Zip_Choice}, which is a nullable {@code smallint} — null on
     * 52,439 of the live table's 52,440 rows (A87). The grid's Yes/No is derived from it in one place,
     * on the screen, so that null and 0 are not quietly conflated here.
     *
     * <p>State, county and city are {@code char} columns and therefore space-padded; they are trimmed,
     * because the padding belongs to the column and not to the name.
     */
    public record ZipRow(int id, String state, String county, String city, Short choice) {

        /**
         * {@code if FieldByName('Zip_Choice').AsString = '1'} — anything else, including null, is No.
         *
         * <p>Named {@code isXxx} deliberately. A record's JSON is its components, plus any accessor
         * Jackson recognises as a getter — and {@code inChoiceNetwork()} is not one, so the field was
         * silently missing from every response and the screen read {@code undefined} and drew *No* on
         * every row. See the JSON test in {@code ZipCodeJsonTest}.
         */
        public boolean isInChoiceNetwork() {
            return choice != null && choice == 1;
        }
    }

    /**
     * US-14.9 — what the Add New Zip Code tab sends.
     *
     * <p>{@code marketArea} is the Market Area checkbox: the Delphi stores {@code Ord(Checked)}, so 0 or
     * 1 (A95).
     */
    public record NewZip(String zip, String city, String state, String county, boolean marketArea) {
    }
}
