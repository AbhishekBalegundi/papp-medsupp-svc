package com.anthem.cartman.appdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.anthem.cartman.appdata.entity.Application;

/** US-7.2, US-7.6, US-7.8 — the application row the screen shows, edits and deletes. */
public interface ApplicationRepository extends JpaRepository<Application, Integer> {
}
