package com.anthem.cartman.zipcodes.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.zipcodes.dto.ZipCodeViews.NewZip;
import com.anthem.cartman.zipcodes.dto.ZipCodeViews.ZipRow;
import com.anthem.cartman.zipcodes.service.ZipCodeService;

/** Module 14 — Zipcode Editor. */
@RestController
@RequestMapping("/api/zipcodes")
public class ZipCodeController {

    private final ZipCodeService service;

    public ZipCodeController(ZipCodeService service) {
        this.service = service;
    }

    /** US-14.4 — the Execute bolt: the rows for one five-digit zip. */
    @GetMapping
    public List<ZipRow> find(@RequestParam(required = false) String zip) {
        return service.find(zip);
    }

    /** US-14.9 — Insert. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZipRow insert(@RequestBody NewZip request) {
        return service.insert(request);
    }

    /** US-14.6 — Delete. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        service.delete(id);
    }

    /** US-14.7 — the two Choice buttons, which differ only in the value they write. */
    @PutMapping("/{id}/choice")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setChoice(@PathVariable int id, @RequestParam boolean inNetwork) {
        service.setChoice(id, inNetwork);
    }
}
