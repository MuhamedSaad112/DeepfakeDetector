package com.deepfakedetector.util;

import com.deepfakedetector.model.entity.MediaFile;
import com.deepfakedetector.model.entity.DetectionResultEntity;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;

public class ReportGenerator {

    public static byte[] generatePdf(MediaFile file) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // عنوان
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            document.add(new Paragraph("Deepfake Detection Report", titleFont));
            document.add(new Paragraph(" ")); // مسافة

            // بيانات الفيديو
            document.add(new Paragraph("📁 File Name: " + file.getFileName()));
            document.add(new Paragraph("🕒 Uploaded At: " + file.getUploadedAt()));
            document.add(new Paragraph("📦 File Size: " + String.format("%.2f MB", file.getFileSizeInMB())));
            document.add(new Paragraph("🎞️ Duration: " + (file.getDuration() != null ? file.getDuration() + " sec" : "N/A")));
            document.add(new Paragraph("🔍 Format: " + file.getFormat()));
            document.add(new Paragraph("📏 Resolution: " + file.getResolution()));
            document.add(new Paragraph("👤 Uploaded By: " + file.getUser().getUserName()));
            document.add(new Paragraph("🔗 Upload Source: " + file.getUploadSource()));
            document.add(new Paragraph("🧠 Is Deepfake: " + (file.getIsDeepfake() ? "Yes" : "No")));
            document.add(new Paragraph(" "));

            // نتيجة التحليل
            DetectionResultEntity result = file.getDetectionResults().stream().findFirst()
                    .orElse(null);

            if (result != null) {
                document.add(new Paragraph("📊 Prediction: " + result.getPredictionLabel()));
                document.add(new Paragraph("✅ Confidence: " + result.getConfidenceScore()));
                document.add(new Paragraph("📈 Accuracy: " + result.getDetectionAccuracy()));
                document.add(new Paragraph("🧪 Method: " + result.getDetectionMethod()));
                document.add(new Paragraph("🧬 Model Version: " + result.getModelVersion()));
                document.add(new Paragraph("👀 Reviewed By: " + (result.getReviewedBy() != null ? result.getReviewedBy() : "N/A")));
            } else {
                document.add(new Paragraph("❗ No detection results available."));
            }

            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
}
