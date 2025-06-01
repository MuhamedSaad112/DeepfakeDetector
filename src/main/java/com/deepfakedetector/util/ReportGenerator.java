package com.deepfakedetector.util;

import com.deepfakedetector.exception.DeepfakeException;
import com.deepfakedetector.exception.DeepfakeSilentException;
import com.deepfakedetector.exception.DetectionErrorCode;
import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.model.entity.User;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ReportGenerator {

    private static final BaseColor PRIMARY_COLOR = new BaseColor(43, 88, 118);
    private static final BaseColor SECONDARY_COLOR = new BaseColor(78, 67, 118);
    private static final BaseColor ACCENT_COLOR = new BaseColor(102, 126, 234);
    private static final BaseColor SUCCESS_COLOR = new BaseColor(46, 160, 67);
    private static final BaseColor DANGER_COLOR = new BaseColor(218, 54, 51);
    private static final BaseColor WARNING_COLOR = new BaseColor(251, 188, 52);
    private static final BaseColor LIGHT_GRAY = new BaseColor(248, 250, 252);
    private static final BaseColor DARK_GRAY = new BaseColor(44, 62, 80);
    private static final BaseColor TEXT_COLOR = new BaseColor(55, 65, 81);
    private static final BaseColor TABLE_HEADER_COLOR = new BaseColor(59, 130, 246);
    private static final BaseColor TABLE_ROW_EVEN = new BaseColor(249, 250, 251);
    private static final BaseColor TABLE_ROW_ODD = BaseColor.WHITE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String REPORT_VERSION = "2.0";
    private final ExecutorService reportProcessingExecutor =
            Executors.newFixedThreadPool(Math.min(3, Runtime.getRuntime().availableProcessors()));
    @Value("${deepfake.report.company-name:Deepfake Detection System}")
    private String companyName;
    @Value("${deepfake.report.include-watermark:true}")
    private boolean includeWatermark;
    @Value("${deepfake.report.max-file-size-mb:50}")
    private long maxReportFileSizeMb;

    public static String getLocalizedDate(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(DATETIME_FORMATTER);
    }

    private Font createFont(int size, int style, BaseColor color) {
        return new Font(Font.FontFamily.HELVETICA, size, style, color);
    }

    public Mono<byte[]> generatePdf(MediaFile file) {
        return generatePdf(file, null);
    }

    public Mono<byte[]> generatePdf(MediaFile file, User authenticatedUser) {
        return Mono.fromCallable(() -> validateAndGenerateEnhancedReport(file))
                .subscribeOn(Schedulers.fromExecutor(reportProcessingExecutor))
                .doOnError(err -> {
                })
                .onErrorMap(this::mapToAppropriateException);
    }

    private byte[] validateAndGenerateEnhancedReport(MediaFile file) throws DeepfakeException {
        validateMediaFile(file);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = createProfessionalDocument();
            PdfWriter writer = PdfWriter.getInstance(document, outputStream);

            setupEnhancedDocumentMetadata(document, file);
            document.open();

            buildEnhancedReportContent(document, file);

            document.close();
            byte[] pdfBytes = outputStream.toByteArray();

            validateGeneratedFileSize(pdfBytes, file.getFileName());
            return pdfBytes;

        } catch (DeepfakeException e) {
            throw e;
        } catch (Exception e) {
            throw new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_GENERATE_REPORT);
        }
    }

    private Document createProfessionalDocument() throws DocumentException {
        Document document = new Document(PageSize.A4, 50, 50, 30, 50);
        document.addLanguage("en");

        document.addCreationDate();
        document.addCreator(companyName);
        document.addTitle("Deepfake Detection Analysis Report");
        document.addSubject("AI-Powered Video Analysis Report");
        document.addKeywords("deepfake, AI detection, video analysis, security, authentication");
        document.addAuthor(companyName + " - AI Detection System");

        return document;
    }

    private void setupEnhancedDocumentMetadata(Document document, MediaFile file) {
        String title = TextResources.getText("title") + " - " + file.getFileName();
        document.addTitle(title);
        document.addSubject("Comprehensive AI Analysis Report for " + file.getFileName());
    }

    private void buildEnhancedReportContent(Document document, MediaFile file) throws DocumentException {
        addEnhancedReportHeader(document, file);
        addStyledFileInformationTable(document, file);
        addColoredDetectionResultsTable(document, file);
        addSummaryAndRecommendations(document, file);
        addProfessionalFooter(document);
    }

    private void addEnhancedReportHeader(Document document, MediaFile file) throws DocumentException {
        String titleText = TextResources.getText("title");
        Font mainTitleFont = createFont(22, Font.BOLD, PRIMARY_COLOR);
        Paragraph mainTitle = new Paragraph("🔍 " + titleText, mainTitleFont);
        mainTitle.setAlignment(Element.ALIGN_CENTER);
        mainTitle.setSpacingAfter(8f);
        document.add(mainTitle);

        String subtitleText = TextResources.getText("subtitle");
        Font subTitleFont = createFont(14, Font.BOLD, SECONDARY_COLOR);
        Paragraph subTitle = new Paragraph(subtitleText, subTitleFont);
        subTitle.setAlignment(Element.ALIGN_CENTER);
        subTitle.setSpacingAfter(20f);
        document.add(subTitle);

        addHeaderInfoTable(document);
        addColoredSeparator(document, PRIMARY_COLOR);
    }

    private void addHeaderInfoTable(Document document) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(80);
        headerTable.setSpacingBefore(10f);
        headerTable.setSpacingAfter(20f);
        headerTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        float[] columnWidths = {33.33f, 33.33f, 33.34f};
        headerTable.setWidths(columnWidths);

        Font headerInfoFont = createFont(9, Font.BOLD, BaseColor.WHITE);
        Font headerValueFont = createFont(8, Font.NORMAL, DARK_GRAY);

        addHeaderInfoCell(headerTable, "📅 " + TextResources.getText("generated"), headerInfoFont, ACCENT_COLOR);
        addHeaderInfoCell(headerTable, "🔢 " + TextResources.getText("version"), headerInfoFont, ACCENT_COLOR);
        addHeaderInfoCell(headerTable, "🏢 " + TextResources.getText("company"), headerInfoFont, ACCENT_COLOR);

        addHeaderInfoCell(headerTable, getLocalizedDate(LocalDateTime.now()), headerValueFont, LIGHT_GRAY);
        addHeaderInfoCell(headerTable, REPORT_VERSION, headerValueFont, LIGHT_GRAY);
        addHeaderInfoCell(headerTable, companyName, headerValueFont, LIGHT_GRAY);

        document.add(headerTable);
    }

    private void addHeaderInfoCell(PdfPTable table, String text, Font font, BaseColor backgroundColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(backgroundColor);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addStyledFileInformationTable(Document document, MediaFile file) throws DocumentException {
        String sectionTitleText = TextResources.getText("fileInfo");
        Font sectionTitleFont = createFont(14, Font.BOLD, ACCENT_COLOR);
        Paragraph sectionTitle = new Paragraph(sectionTitleText, sectionTitleFont);
        sectionTitle.setSpacingBefore(10f);
        sectionTitle.setSpacingAfter(100f);
        document.add(sectionTitle);

        addColoredSeparator(document, ACCENT_COLOR);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(20f);

        float[] columnWidths = {8f, 37f, 55f};
        table.setWidths(columnWidths);

        addEnhancedFileInfoRow(table, "📄", TextResources.getText("fileName"),
                file.getFileName(), false, PRIMARY_COLOR);
        addEnhancedFileInfoRow(table, "🕒", TextResources.getText("uploadDate"),
                file.getUploadedAt() != null ? getLocalizedDate(file.getUploadedAt()) : "N/A", true, SECONDARY_COLOR);
        addEnhancedFileInfoRow(table, "📦", TextResources.getText("fileSize"),
                String.format("%.2f MB", file.getFileSizeInMB()), false, SUCCESS_COLOR);
        addEnhancedFileInfoRow(table, "⏱️", TextResources.getText("duration"),
                file.getDuration() != null ? file.getDuration() + " seconds" : "N/A", true, WARNING_COLOR);
        addEnhancedFileInfoRow(table, "🎞️", TextResources.getText("format"),
                file.getFormat() != null ? file.getFormat().toUpperCase() : "Unknown", false, ACCENT_COLOR);
        addEnhancedFileInfoRow(table, "📐", TextResources.getText("resolution"),
                file.getResolution() != null ? file.getResolution() : "N/A", true, PRIMARY_COLOR);
        addEnhancedFileInfoRow(table, "👤", TextResources.getText("uploadedBy"),
                file.getUser() != null ? file.getUser().getUserName() : "Unknown User", false, SECONDARY_COLOR);
        addEnhancedFileInfoRow(table, "🌐", TextResources.getText("uploadSource"),
                file.getUploadSource() != null ? file.getUploadSource().toString() : "Direct Upload", true, SUCCESS_COLOR);

        document.add(table);
    }

    private void addEnhancedFileInfoRow(PdfPTable table, String icon, String property, String value,
                                        boolean alternateRow, BaseColor accentColor) {
        Font iconFont = createFont(17, Font.NORMAL, BaseColor.BLACK);
        Font propertyFont = createFont(15, Font.BOLD, DARK_GRAY);
        Font valueFont = createFont(19, Font.NORMAL, TEXT_COLOR);

        BaseColor backgroundColor = alternateRow ? TABLE_ROW_EVEN : TABLE_ROW_ODD;

        PdfPCell iconCell = new PdfPCell(new Phrase(icon, iconFont));
        iconCell.setBackgroundColor(backgroundColor);
        iconCell.setPadding(8);
        iconCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        iconCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        iconCell.setBorder(Rectangle.LEFT);
        iconCell.setBorderColor(accentColor);
        iconCell.setBorderWidth(3f);

        PdfPCell propertyCell = new PdfPCell(new Phrase(property, propertyFont));
        propertyCell.setBackgroundColor(backgroundColor);
        propertyCell.setPadding(8);
        propertyCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        propertyCell.setBorder(Rectangle.NO_BORDER);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "N/A", valueFont));
        valueCell.setBackgroundColor(backgroundColor);
        valueCell.setPadding(8);
        valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        valueCell.setBorder(Rectangle.NO_BORDER);

        table.addCell(iconCell);
        table.addCell(propertyCell);
        table.addCell(valueCell);
    }

    private void addColoredDetectionResultsTable(Document document, MediaFile file) throws DocumentException {
        Font sectionTitleFont = createFont(14, Font.BOLD, DANGER_COLOR);
        Paragraph sectionTitle = new Paragraph(TextResources.getText("detectionResults"), sectionTitleFont);
        sectionTitle.setSpacingBefore(25f);
        sectionTitle.setSpacingAfter(30f);
        document.add(sectionTitle);

        addColoredSeparator(document, DANGER_COLOR);

        DetectionResultEntity result = getLatestDetectionResult(file);

        if (result != null) {
            addVerdictDisplayTable(document, file);

            PdfPTable detectionTable = new PdfPTable(3);
            detectionTable.setWidthPercentage(100);
            detectionTable.setSpacingBefore(15f);
            detectionTable.setSpacingAfter(20f);

            float[] columnWidths = {8f, 37f, 55f};
            detectionTable.setWidths(columnWidths);

            addEnhancedDetectionRow(detectionTable, "📊", TextResources.getText("predictionLabel"),
                    result.getPredictionLabel(), false, file.getIsDeepfake() ? DANGER_COLOR : SUCCESS_COLOR);

            double confidence = result.getConfidenceScore() * 100;
            BaseColor confidenceColor = confidence >= 80 ? SUCCESS_COLOR : confidence >= 60 ? WARNING_COLOR : DANGER_COLOR;
            addEnhancedDetectionRow(detectionTable, "✅", TextResources.getText("confidenceScore"),
                    String.format("%.2f%%", confidence), true, confidenceColor);

            double accuracy = result.getDetectionAccuracy() != null ? result.getDetectionAccuracy() * 100 : 0.0;
            BaseColor accuracyColor = accuracy >= 85 ? SUCCESS_COLOR : accuracy >= 70 ? WARNING_COLOR : DANGER_COLOR;
            addEnhancedDetectionRow(detectionTable, "📈", TextResources.getText("detectionAccuracy"),
                    String.format("%.2f%%", accuracy), false, accuracyColor);

            addEnhancedDetectionRow(detectionTable, "🧪", TextResources.getText("detectionMethod"),
                    result.getDetectionMethod() != null ? result.getDetectionMethod().toString() : "N/A", true, ACCENT_COLOR);
            addEnhancedDetectionRow(detectionTable, "🧬", TextResources.getText("modelVersion"),
                    result.getModelVersion() != null ? result.getModelVersion() : "N/A", false, PRIMARY_COLOR);
            addEnhancedDetectionRow(detectionTable, "🕐", TextResources.getText("analysisDate"),
                    result.getCreatedDate() != null ?
                            getLocalizedDate(LocalDateTime.ofInstant(result.getCreatedDate(), ZoneId.systemDefault())) : "N/A", true, SECONDARY_COLOR);
            addEnhancedDetectionRow(detectionTable, "👨‍💼", TextResources.getText("reviewedBy"),
                    result.getReviewedBy() != null ? result.getReviewedBy() : "Automated AI System", false, SUCCESS_COLOR);

            document.add(detectionTable);

        } else {
            addNoResultsWarning(document);
        }
    }

    private void addVerdictDisplayTable(Document document, MediaFile file) throws DocumentException {
        PdfPTable verdictTable = new PdfPTable(1);
        verdictTable.setWidthPercentage(80);
        verdictTable.setSpacingBefore(20f);
        verdictTable.setSpacingAfter(20f);
        verdictTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        BaseColor verdictColor = file.getIsDeepfake() ? DANGER_COLOR : SUCCESS_COLOR;
        String verdictText = file.getIsDeepfake() ?
                TextResources.getText("deepfakeDetected") :
                TextResources.getText("authenticVideo");

        Font verdictFont = createFont(19, Font.BOLD, BaseColor.WHITE);

        String fullVerdictText = "🎯 " + TextResources.getText("finalVerdict") + "\n" + verdictText;
        PdfPCell verdictCell = new PdfPCell(new Phrase(fullVerdictText, verdictFont));
        verdictCell.setBackgroundColor(verdictColor);
        verdictCell.setPadding(12);
        verdictCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        verdictCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        verdictCell.setBorder(Rectangle.NO_BORDER);
        verdictCell.setMinimumHeight(40f);

        verdictTable.addCell(verdictCell);
        document.add(verdictTable);
    }

    private void addEnhancedDetectionRow(PdfPTable table, String icon, String property, String value,
                                         boolean alternateRow, BaseColor accentColor) {
        Font iconFont = createFont(17, Font.NORMAL, BaseColor.BLACK);
        Font propertyFont = createFont(15, Font.BOLD, DARK_GRAY);
        Font valueFont = createFont(14, Font.BOLD, accentColor);

        BaseColor backgroundColor = alternateRow ? TABLE_ROW_EVEN : TABLE_ROW_ODD;

        PdfPCell iconCell = new PdfPCell(new Phrase(icon, iconFont));
        iconCell.setBackgroundColor(backgroundColor);
        iconCell.setPadding(8);
        iconCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        iconCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        iconCell.setBorder(Rectangle.LEFT);
        iconCell.setBorderColor(accentColor);
        iconCell.setBorderWidth(3f);

        PdfPCell propertyCell = new PdfPCell(new Phrase(property, propertyFont));
        propertyCell.setBackgroundColor(backgroundColor);
        propertyCell.setPadding(8);
        propertyCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        propertyCell.setBorder(Rectangle.NO_BORDER);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "N/A", valueFont));
        valueCell.setBackgroundColor(backgroundColor);
        valueCell.setPadding(8);
        valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        valueCell.setBorder(Rectangle.NO_BORDER);

        table.addCell(iconCell);
        table.addCell(propertyCell);
        table.addCell(valueCell);
    }

    private void addNoResultsWarning(Document document) throws DocumentException {
        PdfPTable warningTable = new PdfPTable(1);
        warningTable.setWidthPercentage(90);
        warningTable.setSpacingBefore(10f);
        warningTable.setSpacingAfter(15f);
        warningTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        Font warningFont = createFont(16, Font.BOLD, BaseColor.WHITE);
        Font explanationFont = createFont(14, Font.NORMAL, BaseColor.WHITE);

        String warningText = TextResources.getText("noResults");
        String explanationText = TextResources.getText("noResultsExplanation");

        Paragraph warningPara = new Paragraph();
        warningPara.add(new Chunk(warningText, warningFont));
        warningPara.add(Chunk.NEWLINE);
        warningPara.add(new Chunk(explanationText, explanationFont));

        PdfPCell warningCell = new PdfPCell(warningPara);
        warningCell.setBackgroundColor(WARNING_COLOR);
        warningCell.setPadding(12);
        warningCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        warningCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        warningCell.setBorder(Rectangle.NO_BORDER);
        warningCell.setMinimumHeight(50f);

        warningTable.addCell(warningCell);
        document.add(warningTable);
    }

    private void addSummaryAndRecommendations(Document document, MediaFile file) throws DocumentException {
        Font sectionTitleFont = createFont(19, Font.BOLD, SECONDARY_COLOR);
        Paragraph sectionTitle = new Paragraph(TextResources.getText("summaryRecommendations"), sectionTitleFont);
        sectionTitle.setSpacingBefore(20f);
        sectionTitle.setSpacingAfter(15f);
        document.add(sectionTitle);

        addColoredSeparator(document, SECONDARY_COLOR);

        Font contentFont = createFont(15, Font.NORMAL, TEXT_COLOR);

        String summaryText = file.getIsDeepfake() ?
                TextResources.getText("deepfakeSummary") :
                TextResources.getText("authenticSummary");

        Paragraph summary = new Paragraph(summaryText, contentFont);
        summary.setAlignment(Element.ALIGN_JUSTIFIED);
        summary.setSpacingAfter(12f);
        document.add(summary);

        Font recommendationFont = createFont(14, Font.ITALIC, DARK_GRAY);
        String recommendationText = TextResources.getText("securityRecommendations");

        Paragraph recommendations = new Paragraph(recommendationText, recommendationFont);
        recommendations.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(recommendations);
    }

    private void addProfessionalFooter(Document document) throws DocumentException {
        addColoredSeparator(document, PRIMARY_COLOR);

        Font footerFont = createFont(13, Font.NORMAL, DARK_GRAY);

        String footerText = String.format("🛡️ %s %s AI Detection System.\n🔒 %s\n📞 %s",
                TextResources.getText("footerGenerated"),
                companyName,
                TextResources.getText("footerConfidential"),
                TextResources.getText("footerSupport"));

        Paragraph footer = new Paragraph(footerText, footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(12f);
        document.add(footer);

        if (includeWatermark) {
            addColoredWatermark(document);
        }
    }

    private void addColoredWatermark(Document document) throws DocumentException {
        Font watermarkFont = createFont(12, Font.ITALIC, ACCENT_COLOR);

        String watermarkText = TextResources.getText("watermark") + " - " + companyName.toUpperCase();

        Paragraph watermark = new Paragraph(watermarkText, watermarkFont);
        watermark.setAlignment(Element.ALIGN_CENTER);
        watermark.setSpacingBefore(8f);
        document.add(watermark);
    }

    private void addColoredSeparator(Document document, BaseColor color) throws DocumentException {
        LineSeparator separator = new LineSeparator();
        separator.setLineColor(color);
        separator.setLineWidth(2f);
        Chunk linebreak = new Chunk(separator);
        Paragraph separatorParagraph = new Paragraph(linebreak);
        separatorParagraph.setSpacingAfter(12f);
        document.add(separatorParagraph);
    }

    private DetectionResultEntity getLatestDetectionResult(MediaFile file) {
        java.util.List<DetectionResultEntity> results = file.getDetectionResults();

        if (results != null && !results.isEmpty()) {
            return results.stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparing(DetectionResultEntity::getPredictedAt))
                    .orElse(null);
        } else {
            return null;
        }
    }

    private void validateMediaFile(MediaFile file) throws DeepfakeException {
        if (file == null) {
            throw new DeepfakeException(DetectionErrorCode.MEDIA_FILE_NOT_FOUND);
        }

        if (file.getFileName() == null || file.getFileName().isBlank()) {
            throw new DeepfakeException(DetectionErrorCode.INVALID_MEDIA_FILE);
        }
    }

    private void validateGeneratedFileSize(byte[] pdfBytes, String fileName) throws DeepfakeException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new DeepfakeException(DetectionErrorCode.EMPTY_REPORT_GENERATED);
        }

        long fileSizeMb = pdfBytes.length / (1024L * 1024L);
        if (fileSizeMb > maxReportFileSizeMb) {
            throw new DeepfakeException(DetectionErrorCode.REPORT_FILE_TOO_LARGE);
        }
    }

    private Throwable mapToAppropriateException(Throwable error) {
        if (error instanceof DeepfakeException || error instanceof DeepfakeSilentException) {
            return error;
        }

        if (error instanceof DocumentException) {
            return new DeepfakeSilentException(DetectionErrorCode.PDF_GENERATION_ERROR);
        }

        if (error instanceof OutOfMemoryError) {
            return new DeepfakeSilentException(DetectionErrorCode.INSUFFICIENT_MEMORY);
        }

        if (error instanceof RuntimeException && error.getCause() instanceof DeepfakeException) {
            return error.getCause();
        }

        return new DeepfakeSilentException(DetectionErrorCode.FAILED_TO_GENERATE_REPORT);
    }

    @PreDestroy
    public void cleanup() {
        reportProcessingExecutor.shutdown();
        try {
            if (!reportProcessingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                reportProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            reportProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class TextResources {
        public static String getText(String key) {
            switch (key) {
                case "title":
                    return "DEEPFAKE DETECTION REPORT";
                case "subtitle":
                    return "AI-Powered Video Analysis System";
                case "generated":
                    return "Generated";
                case "version":
                    return "Version";
                case "company":
                    return "Company";
                case "fileInfo":
                    return "📁 VIDEO INFORMATION";
                case "detectionResults":
                    return "🔬 DETECTION RESULTS";
                case "summaryRecommendations":
                    return "📋 SUMMARY & RECOMMENDATIONS";
                case "fileName":
                    return "File Name";
                case "uploadDate":
                    return "Upload Date";
                case "fileSize":
                    return "File Size";
                case "duration":
                    return "Duration";
                case "format":
                    return "Format";
                case "resolution":
                    return "Resolution";
                case "uploadedBy":
                    return "Uploaded By";
                case "uploadSource":
                    return "Upload Source";
                case "finalVerdict":
                    return "FINAL VERDICT";
                case "predictionLabel":
                    return "Prediction Label";
                case "confidenceScore":
                    return "Confidence Score";
                case "detectionAccuracy":
                    return "Detection Accuracy";
                case "detectionMethod":
                    return "Detection Method";
                case "modelVersion":
                    return "Model Version";
                case "analysisDate":
                    return "Analysis Date";
                case "reviewedBy":
                    return "Reviewed By";
                case "deepfakeDetected":
                    return "⚠️ DEEPFAKE DETECTED";
                case "authenticVideo":
                    return "✅ AUTHENTIC VIDEO";
                case "deepfakeSummary":
                    return "🚨 This video has been identified as a potential deepfake. Exercise caution when sharing or using this content. Consider additional verification through multiple detection systems.";
                case "authenticSummary":
                    return "✅ This video appears to be authentic based on our AI analysis. However, continue practicing media literacy and verify content from multiple sources when necessary.";
                case "securityRecommendations":
                    return "🔒 Security Recommendations: Always verify suspicious content through multiple channels, keep detection systems updated, and report potential deepfakes to relevant authorities.";
                case "noResults":
                    return "⚠️ WARNING: No detection results available for this file.";
                case "noResultsExplanation":
                    return "This could indicate a processing error, incomplete analysis, or unsupported file format.";
                case "footerGenerated":
                    return "This report was generated by";
                case "footerConfidential":
                    return "This document contains confidential analysis results - Handle with care.";
                case "footerSupport":
                    return "Technical Support: support@deepfakedetection.com | 🌐 www.deepfakedetection.com";
                case "watermark":
                    return "🔐 CONFIDENTIAL ANALYSIS";
                default:
                    return key;
            }
        }
    }
}