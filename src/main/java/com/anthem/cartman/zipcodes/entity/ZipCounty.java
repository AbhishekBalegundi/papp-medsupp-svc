package com.anthem.cartman.zipcodes.entity;

import com.anthem.cartman.common.util.TrimmedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code TM_Cd_ZipCnty} — the zip-code reference table, 52,440 rows.
 *
 * <p>Thirteen columns, of which the Zip Editor reads or writes eight. The other five —
 * {@code ZIP_Territory}, {@code ZIP_ASA_Terr}, {@code Zip_Sup_Area}, {@code Zip_Sel_Area} and
 * {@code Zip_County_Code} — are mapped so that an update touches nothing it did not mean to, and are
 * never set by this module: an insert leaves them null, exactly as the Delphi's does (A97).
 *
 * <p>{@code Zip_ID} is an int identity and the table's only index.
 */
@Entity
@Table(name = "TM_Cd_ZipCnty")
@DynamicUpdate
public class ZipCounty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Zip_ID")
    private Integer id;

    /**
     * The zip the Delphi searches on, {@code char(10)}.
     *
     * <p>Null on 404 of the live rows, every one of which carries a zip in {@link #zipDup1} instead —
     * so those rows cannot be found from this screen (A89).
     */
    @Column(name = "Zip_Zip_Cd", length = 10)
    @Convert(converter = TrimmedStringConverter.class)
    private String zip;

    /**
     * {@code char(9) NOT NULL}. The insert writes the same zip into this and into {@link #zip} (A89).
     */
    @Column(name = "Zip_Zip_Dup1", length = 9, nullable = false)
    @Convert(converter = TrimmedStringConverter.class)
    private String zipDup1;

    @Column(name = "Zip_City", length = 20)
    @Convert(converter = TrimmedStringConverter.class)
    private String city;

    @Column(name = "Zip_St", length = 2)
    @Convert(converter = TrimmedStringConverter.class)
    private String state;

    @Column(name = "Zip_County", length = 20)
    @Convert(converter = TrimmedStringConverter.class)
    private String county;

    /** {@code smallint} — 0 or 1 from the checkbox, though 37 live rows hold 2 (A95). */
    @Column(name = "Zip_Market")
    private Short market;

    /** {@code smallint}, and null on all but one live row (A87). */
    @Column(name = "Zip_Choice")
    private Short choice;

    protected ZipCounty() {
    }

    public ZipCounty(String zip, String city, String state, String county, short market, short choice) {
        this.zip = zip;
        // A89 — both columns take the zip, as the Delphi's insert does.
        this.zipDup1 = zip;
        this.city = city;
        this.state = state;
        this.county = county;
        this.market = market;
        this.choice = choice;
    }

    public Integer getId() {
        return id;
    }

    public String getZip() {
        return zip;
    }

    public String getZipDup1() {
        return zipDup1;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getCounty() {
        return county;
    }

    public Short getMarket() {
        return market;
    }

    public Short getChoice() {
        return choice;
    }

    /** US-14.7 — the only column either Choice button changes. */
    public void setChoice(Short choice) {
        this.choice = choice;
    }
}
