package com.healthlog.controller;

import com.healthlog.dto.VitalSignRequest;
import com.healthlog.entity.VitalSign;
import com.healthlog.service.VitalSignService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 生命徵象 REST 控制器（血壓/血糖/心率/體溫） */
@RestController
@RequestMapping("/vital-signs")
public class VitalSignController {

    private final VitalSignService service;

    public VitalSignController(VitalSignService service) {
        this.service = service;
    }

    @GetMapping
    public List<VitalSign> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return (from != null && to != null) ? service.findByRange(from, to) : service.findAll();
    }

    @PostMapping
    public ResponseEntity<VitalSign> create(@Valid @RequestBody VitalSignRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public VitalSign update(@PathVariable Long id, @Valid @RequestBody VitalSignRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
