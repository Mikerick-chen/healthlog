package com.healthlog.controller;

import com.healthlog.dto.BodyMetricRequest;
import com.healthlog.entity.BodyMetric;
import com.healthlog.service.BodyMetricService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 身體數據 REST 控制器（身高/體重/BMI/喝水量） */
@RestController
@RequestMapping("/body-metrics")
public class BodyMetricController {

    private final BodyMetricService service;

    public BodyMetricController(BodyMetricService service) {
        this.service = service;
    }

    @GetMapping
    public List<BodyMetric> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return (from != null && to != null) ? service.findByRange(from, to) : service.findAll();
    }

    @PostMapping
    public ResponseEntity<BodyMetric> create(@Valid @RequestBody BodyMetricRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public BodyMetric update(@PathVariable Long id, @Valid @RequestBody BodyMetricRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
