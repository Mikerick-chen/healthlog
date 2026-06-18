package com.healthlog.controller;

import com.healthlog.service.ExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** 報表匯出控制器（§5：Excel + PDF 診斷報告書，取代 CSV） */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ExportService exportService;

    public ReportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /** GET /reports/excel — 匯出 Excel(.xlsx)，含健康日誌/身體數據/生命徵象三工作表 */
    @GetMapping("/excel")
    public ResponseEntity<Resource> excel() {
        byte[] data = exportService.exportExcel();
        String filename = encode("健康數據_" + LocalDate.now() + ".xlsx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(data));
    }

    /** GET /reports/pdf — 匯出 PDF 健康診斷報告書 */
    @GetMapping("/pdf")
    public ResponseEntity<Resource> pdf() {
        byte[] data = exportService.exportPdfReport();
        String filename = encode("健康診斷報告_" + LocalDate.now() + ".pdf");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(data));
    }

    private String encode(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
