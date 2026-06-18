package com.healthlog.service;

import com.healthlog.dto.AssessmentResult;
import com.healthlog.dto.AssessmentResult.Metric;
import com.healthlog.entity.BodyMetric;
import com.healthlog.entity.HealthLog;
import com.healthlog.entity.VitalSign;
import com.healthlog.repository.BodyMetricRepository;
import com.healthlog.repository.HealthLogRepository;
import com.healthlog.repository.VitalSignRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 匯出服務（§5 需求）：
 *  - Excel (.xlsx)：以 Apache POI 輸出健康日誌 + 身體數據 + 生命徵象三個工作表。
 *  - PDF 診斷報告書：以 OpenPDF 輸出含綜合評估、各項判讀與健康建議的報告（嵌入中文字型）。
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HealthLogRepository healthLogRepo;
    private final BodyMetricRepository bodyRepo;
    private final VitalSignRepository vitalRepo;
    private final HealthAssessmentService assessment;

    public ExportService(HealthLogRepository healthLogRepo,
                         BodyMetricRepository bodyRepo,
                         VitalSignRepository vitalRepo,
                         HealthAssessmentService assessment) {
        this.healthLogRepo = healthLogRepo;
        this.bodyRepo = bodyRepo;
        this.vitalRepo = vitalRepo;
        this.assessment = assessment;
    }

    // ==================== Excel ====================

    public byte[] exportExcel() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(wb);

            // 工作表一：健康日誌
            Sheet s1 = wb.createSheet("健康日誌");
            writeRow(s1, 0, header, "日期", "睡眠(hr)", "步數", "心情", "風險等級");
            int r = 1;
            for (HealthLog e : healthLogRepo.findAllByOrderByLogDateDesc()) {
                Row row = s1.createRow(r++);
                row.createCell(0).setCellValue(e.getLogDate().format(DF));
                row.createCell(1).setCellValue(e.getSleepHours());
                row.createCell(2).setCellValue(e.getSteps());
                row.createCell(3).setCellValue(e.getMoodScore());
                row.createCell(4).setCellValue(e.getRiskLevel() == null ? "" : e.getRiskLevel());
            }
            autoSize(s1, 5);

            // 工作表二：身體數據
            Sheet s2 = wb.createSheet("身體數據");
            writeRow(s2, 0, header, "日期", "身高(cm)", "體重(kg)", "BMI", "喝水(ml)");
            r = 1;
            for (BodyMetric e : bodyRepo.findAllByOrderByRecordDateDesc()) {
                Row row = s2.createRow(r++);
                row.createCell(0).setCellValue(e.getRecordDate().format(DF));
                if (e.getHeightCm() != null) row.createCell(1).setCellValue(e.getHeightCm());
                if (e.getWeightKg() != null) row.createCell(2).setCellValue(e.getWeightKg());
                if (e.getBmi() != null) row.createCell(3).setCellValue(e.getBmi());
                if (e.getWaterMl() != null) row.createCell(4).setCellValue(e.getWaterMl());
            }
            autoSize(s2, 5);

            // 工作表三：生命徵象
            Sheet s3 = wb.createSheet("生命徵象");
            writeRow(s3, 0, header, "日期", "收縮壓", "舒張壓", "心率", "體溫(℃)", "血糖(mg/dL)", "量測情境");
            r = 1;
            for (VitalSign e : vitalRepo.findAllByOrderByRecordDateDesc()) {
                Row row = s3.createRow(r++);
                row.createCell(0).setCellValue(e.getRecordDate().format(DF));
                if (e.getSystolic() != null) row.createCell(1).setCellValue(e.getSystolic());
                if (e.getDiastolic() != null) row.createCell(2).setCellValue(e.getDiastolic());
                if (e.getHeartRate() != null) row.createCell(3).setCellValue(e.getHeartRate());
                if (e.getBodyTemp() != null) row.createCell(4).setCellValue(e.getBodyTemp());
                if (e.getBloodSugar() != null) row.createCell(5).setCellValue(e.getBloodSugar());
                row.createCell(6).setCellValue(e.getMeasureContext() == null ? "" : e.getMeasureContext());
            }
            autoSize(s3, 7);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Excel 產生失敗：" + ex.getMessage(), ex);
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(f);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeRow(Sheet sheet, int rowIdx, CellStyle style, String... values) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < values.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(values[i]);
            c.setCellStyle(style);
        }
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) sheet.setColumnWidth(i, 4000);
    }

    // ==================== PDF 診斷報告書 ====================

    public byte[] exportPdfReport() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 54, 54);
            PdfWriter.getInstance(doc, out);
            doc.open();

            BaseFont bf = loadCjkBaseFont();
            Font titleFont = new Font(bf, 20, Font.BOLD, new Color(37, 99, 235));
            Font h2Font = new Font(bf, 13, Font.BOLD, new Color(30, 41, 59));
            Font normal = new Font(bf, 11, Font.NORMAL, Color.BLACK);
            Font small = new Font(bf, 9, Font.NORMAL, new Color(107, 114, 128));

            AssessmentResult a = assessment.assessLatest();

            // 標題
            Paragraph title = new Paragraph("健康診斷報告書", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            Paragraph sub = new Paragraph("智慧健康日誌與風險評估系統 ｜ 報告日期：" + a.asOfDate.format(DF), small);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(16);
            doc.add(sub);

            // 綜合健康分數區塊
            doc.add(sectionTitle("一、綜合健康評估", h2Font));
            PdfPTable scoreTable = new PdfPTable(2);
            scoreTable.setWidthPercentage(100);
            scoreTable.addCell(kv("綜合健康分數", a.compositeScore + " / 100（" + a.scoreGrade + "）", normal, bf));
            scoreTable.addCell(kv("決策樹風險等級", a.decisionTreeRisk == null ? "（無資料）" : a.decisionTreeRisk, normal, bf));
            scoreTable.setSpacingAfter(12);
            doc.add(scoreTable);

            // 各項指標判讀
            doc.add(sectionTitle("二、各項健康指標判讀", h2Font));
            PdfPTable t = new PdfPTable(new float[]{2, 3, 2, 4});
            t.setWidthPercentage(100);
            for (String head : new String[]{"指標", "數值", "判讀", "說明"}) {
                PdfPCell c = new PdfPCell(new Phrase(head, new Font(bf, 11, Font.BOLD, Color.WHITE)));
                c.setBackgroundColor(new Color(37, 99, 235));
                c.setPadding(6);
                t.addCell(c);
            }
            for (Metric m : new Metric[]{a.bmi, a.bloodPressure, a.bloodSugar, a.heartRate, a.bodyTemp, a.water}) {
                if (m == null) continue;
                t.addCell(cell(m.name, normal));
                t.addCell(cell(m.value, normal));
                t.addCell(cell(m.level, new Font(bf, 11, Font.BOLD, statusColor(m.status))));
                t.addCell(cell(m.note, small));
            }
            t.setSpacingAfter(12);
            doc.add(t);

            // 決策樹判斷路徑
            if (a.riskDetail != null && a.riskDetail.getDecisionPath() != null) {
                doc.add(sectionTitle("三、風險決策樹判斷路徑", h2Font));
                com.lowagie.text.List path = new com.lowagie.text.List(true, 14);
                for (String step : a.riskDetail.getDecisionPath()) {
                    path.add(new ListItem(step, normal));
                }
                doc.add(path);
                doc.add(new Paragraph(" ", small));
            }

            // 健康建議
            doc.add(sectionTitle("四、健康建議", h2Font));
            com.lowagie.text.List advice = new com.lowagie.text.List(true, 14);
            for (String rec : a.recommendations) advice.add(new ListItem(rec, normal));
            doc.add(advice);

            // 免責聲明
            Paragraph disclaimer = new Paragraph(
                    "\n＊本報告之分類門檻為一般衛教參考值，僅供自我健康管理參考，不構成醫療診斷。如有疑慮請諮詢專業醫師。",
                    small);
            disclaimer.setSpacingBefore(16);
            doc.add(disclaimer);

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("PDF 產生失敗：" + ex.getMessage(), ex);
        }
    }

    /** 載入嵌入式中文字型（classpath:fonts/cjk.ttf），確保 Linux/Zeabur 也能正確顯示中文 */
    private BaseFont loadCjkBaseFont() throws Exception {
        try (InputStream is = new ClassPathResource("fonts/cjk.ttf").getInputStream()) {
            byte[] bytes = is.readAllBytes();
            return BaseFont.createFont("cjk.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, bytes, null);
        } catch (Exception e) {
            log.warn("載入中文字型失敗，改用內建字型（中文可能無法顯示）：{}", e.getMessage());
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        }
    }

    private Paragraph sectionTitle(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(8);
        p.setSpacingAfter(6);
        return p;
    }

    private PdfPCell kv(String k, String v, Font font, BaseFont bf) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(k + "：", new Font(bf, 11, Font.BOLD, Color.BLACK)));
        phrase.add(new Chunk(v, font));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setPadding(8);
        cell.setBackgroundColor(new Color(239, 246, 255));
        return cell;
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(5);
        return c;
    }

    private Color statusColor(String status) {
        return switch (status == null ? "" : status) {
            case "good" -> new Color(22, 163, 74);
            case "warn" -> new Color(245, 158, 11);
            case "danger" -> new Color(220, 38, 38);
            default -> Color.BLACK;
        };
    }
}
