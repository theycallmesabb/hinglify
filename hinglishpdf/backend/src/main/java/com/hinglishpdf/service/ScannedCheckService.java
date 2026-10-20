package com.hinglishpdf.service;

import com.hinglishpdf.dto.ExtractorResponse;
import org.springframework.stereotype.Service;

@Service
public class ScannedCheckService {

    public static final String SCANNED_ERROR_MESSAGE = "This PDF looks scanned. Scanned PDFs are not supported yet.";
    public static final double MIN_AVG_CHARS_PER_PAGE = 50.0;

    /**
     * Checks if the extracted PDF content indicates a scanned document (insufficient text).
     * @param response the ExtractorResponse from PyMuPDF
     * @return true if PDF appears scanned, false otherwise
     */
    public boolean isScanned(ExtractorResponse response) {
        if (response == null || response.pageCount() == 0) {
            return true;
        }
        if (response.totalChars() == 0) {
            return true;
        }
        return response.avgCharsPerPage() < MIN_AVG_CHARS_PER_PAGE;
    }
}
