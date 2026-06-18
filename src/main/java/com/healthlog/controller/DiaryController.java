package com.healthlog.controller;

import com.healthlog.dto.DiaryRequest;
import com.healthlog.entity.DiaryEntry;
import com.healthlog.service.DiaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 健康日記 REST 控制器 */
@RestController
@RequestMapping("/diary")
public class DiaryController {

    private final DiaryService service;

    public DiaryController(DiaryService service) {
        this.service = service;
    }

    @GetMapping
    public List<DiaryEntry> list() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<DiaryEntry> create(@Valid @RequestBody DiaryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public DiaryEntry update(@PathVariable Long id, @Valid @RequestBody DiaryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
