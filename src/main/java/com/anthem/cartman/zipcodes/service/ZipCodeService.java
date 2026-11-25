package com.anthem.cartman.zipcodes.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.zipcodes.dto.ZipCodeViews.NewZip;
import com.anthem.cartman.zipcodes.dto.ZipCodeViews.ZipRow;
import com.anthem.cartman.zipcodes.entity.ZipCounty;
import com.anthem.cartman.zipcodes.repository.ZipCountyRepository;

/**
 * Module 14 — Zipcode Editor ({@code frmZipEditor}).
 *
 * <p>Two tabs' worth of work against one table: find the rows for a zip, remove one, move one in or out
 * of the Choice network, and insert a new one.
 */
@Service
public class ZipCodeService {

    /** {@code if (Length(edZipCode.Text) = 5)} — the form reads nothing on any other length. */
    private static final int ZIP_LENGTH = 5;

    /** {@code Zip_St} is {@code char(2)}, and the insert check is a length of exactly 2. */
    private static final int STATE_LENGTH = 2;

    private final ZipCountyRepository repository;

    public ZipCodeService(ZipCountyRepository repository) {
        this.repository = repository;
    }

    /**
     * US-14.4 — the rows for one zip, by county then city.
     *
     * <p>US-14.3.2: anything but five digits reads nothing. The form simply leaves the grid alone and
     * hides its buttons, so this answers an empty list rather than an error.
     */
    @Transactional(readOnly = true)
    public List<ZipRow> find(String zip) {
        String value = zip == null ? "" : zip.trim();
        if (value.length() != ZIP_LENGTH || !value.chars().allMatch(Character::isDigit)) {
            return List.of();
        }
        return repository.findByZip(value);
    }

    /**
     * US-14.6 — remove one row.
     *
     * <p>A94: nothing is confirmed, because the form confirms nothing. What is added is an answer when
     * the row is not there: the Delphi reports only a raised exception, so a statement that matched
     * nothing looked like a success and the row left the grid anyway.
     */
    @Transactional
    public void delete(int id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("That zip code row is no longer in the table.");
        }
        repository.deleteById(id);
    }

    /**
     * US-14.7 — in or out of the Choice network.
     *
     * <p>One column, one row, addressed by {@code Zip_ID}. A87: the column is null on all but one of the
     * live rows, so this is in practice the only thing that ever puts a value in it.
     */
    @Transactional
    public void setChoice(int id, boolean inNetwork) {
        ZipCounty row = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("That zip code row is no longer in the table."));
        row.setChoice((short) (inNetwork ? 1 : 0));
    }

    /**
     * US-14.9 — insert one row.
     *
     * <p>A88: the Delphi runs all four checks, each with its own message box, and leaves the cursor in
     * whichever failed last. The same four checks run here and are reported together, in the order the
     * form declares them, so the first one named is the first field to fix.
     */
    @Transactional
    public ZipRow insert(NewZip request) {
        String zip = value(request.zip());
        String city = value(request.city());
        String state = value(request.state());
        String county = value(request.county());

        List<String> faults = new ArrayList<>();
        if (zip.length() != ZIP_LENGTH || !zip.chars().allMatch(Character::isDigit)) {
            faults.add("Zip Code Length Should Be 5 Digits");
        }
        if (city.isEmpty()) {
            faults.add("Zip City Can Not Be Blank");
        }
        if (state.length() != STATE_LENGTH) {
            faults.add("Zip State Length Should Be 2 Characters");
        }
        if (county.isEmpty()) {
            faults.add("Zip County Can Not Be Blank");
        }
        if (!faults.isEmpty()) {
            throw new ValidationException(String.join(" ", faults));
        }

        // A90 — Zip_Choice is 0, always: the form has no control for it on this tab.
        ZipCounty saved = repository.save(new ZipCounty(
                zip, city, state, county, (short) (request.marketArea() ? 1 : 0), (short) 0));
        return new ZipRow(saved.getId(), saved.getState(), saved.getCounty(), saved.getCity(),
                saved.getChoice());
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }
}
