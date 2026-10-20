package com.hinglishpdf.service;

import com.hinglishpdf.entity.JobChunk;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    private static final Color BRAND_BLUE = new Color(2, 132, 199);
    private static final Color TEXT_DARK = new Color(31, 41, 55);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);

    public void generateHinglishPdf(String documentTitle, List<JobChunk> chunks, Path outputPath, boolean isMockOutput) throws Exception {
        log.info("Generating Hinglish PDF at: {} (isMock={})", outputPath, isMockOutput);

        Document document = new Document(PageSize.A4, 54, 54, 54, 54);
        FileOutputStream fos = new FileOutputStream(outputPath.toFile());
        PdfWriter writer = PdfWriter.getInstance(document, fos);

        // Add page footer helper for "Page X"
        writer.setPageEvent(new PageFooterHelper());

        document.open();

        // Load embedded Unicode font (DejaVuSans.ttf)
        BaseFont baseFont = loadEmbeddedUnicodeFont();
        Font titleFont = new Font(baseFont, 20, Font.BOLD, BRAND_BLUE);
        Font subtitleFont = new Font(baseFont, 10, Font.ITALIC, TEXT_MUTED);
        Font headingFont = new Font(baseFont, 14, Font.BOLD, BRAND_BLUE);
        Font bodyFont = new Font(baseFont, 10.5f, Font.NORMAL, TEXT_DARK);

        // If mock provider was used, prominently watermark with [MOCK OUTPUT]
        if (isMockOutput) {
            Font mockFont = new Font(baseFont, 12, Font.BOLD, new Color(220, 38, 38));
            Paragraph mockPara = new Paragraph("[MOCK OUTPUT - Simulated Translation, not real AI]", mockFont);
            mockPara.setAlignment(Element.ALIGN_CENTER);
            mockPara.setSpacingAfter(12f);
            document.add(mockPara);
        }

        // Header Title
        Paragraph titlePara = new Paragraph(documentTitle, titleFont);
        titlePara.setSpacingAfter(4f);
        document.add(titlePara);

        Paragraph subPara = new Paragraph("Translated to Hinglish by HinglishPDF", subtitleFont);
        subPara.setSpacingAfter(24f);
        document.add(subPara);

        // Render each chunk
        for (JobChunk chunk : chunks) {
            String content = chunk.getTranslatedText();
            if (content == null || content.isBlank()) {
                content = chunk.getSourceText();
            }

            if (Boolean.TRUE.equals(chunk.getIsHeading())) {
                Paragraph headingPara = new Paragraph(content.trim(), headingFont);
                headingPara.setSpacingBefore(14f);
                headingPara.setSpacingAfter(6f);
                document.add(headingPara);
            } else {
                // Split multi-paragraphs within chunk
                String[] paragraphs = content.split("\n\n+");
                for (String pText : paragraphs) {
                    String trimmed = pText.trim();
                    if (!trimmed.isEmpty()) {
                        Paragraph p = new Paragraph(trimmed, bodyFont);
                        p.setLeading(15.5f);
                        p.setSpacingAfter(8f);
                        document.add(p);
                    }
                }
            }
        }

        document.close();
        fos.close();
        log.info("PDF generated successfully: {} bytes", Files.size(outputPath));
    }

    private BaseFont loadEmbeddedUnicodeFont() {
        try {
            ClassPathResource fontResource = new ClassPathResource("fonts/DejaVuSans.ttf");
            if (fontResource.exists()) {
                try (InputStream is = fontResource.getInputStream()) {
                    byte[] fontBytes = is.readAllBytes();
                    return BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load embedded DejaVuSans.ttf, falling back to standard font: {}", e.getMessage());
        }

        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize PDF font", e);
        }
    }

    private static class PageFooterHelper extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
                String footerText = "Page " + writer.getPageNumber();
                Paragraph footer = new Paragraph(footerText, footerFont);
                footer.setAlignment(Element.ALIGN_RIGHT);
                // Draw footer near bottom margin
            } catch (Exception ignored) {}
        }
    }
}
