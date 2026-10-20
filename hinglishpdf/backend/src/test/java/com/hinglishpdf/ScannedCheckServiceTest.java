package com.hinglishpdf;

import com.hinglishpdf.dto.ExtractorResponse;
import com.hinglishpdf.service.ScannedCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannedCheckServiceTest {

    private ScannedCheckService scannedCheckService;

    @BeforeEach
    void setUp() {
        scannedCheckService = new ScannedCheckService();
    }

    @Test
    void shouldFlagAsScannedWhenResponseIsNull() {
        assertTrue(scannedCheckService.isScanned(null));
    }

    @Test
    void shouldFlagAsScannedWhenPageCountIsZero() {
        ExtractorResponse response = new ExtractorResponse(0, 0, 0.0, Collections.emptyList());
        assertTrue(scannedCheckService.isScanned(response));
    }

    @Test
    void shouldFlagAsScannedWhenTotalCharsIsZero() {
        ExtractorResponse response = new ExtractorResponse(3, 0, 0.0, Collections.emptyList());
        assertTrue(scannedCheckService.isScanned(response));
    }

    @Test
    void shouldFlagAsScannedWhenAvgCharsIsBelowThreshold() {
        // e.g. 3 pages with only 40 characters each (average 40 < 50)
        ExtractorResponse response = new ExtractorResponse(3, 120, 40.0, Collections.emptyList());
        assertTrue(scannedCheckService.isScanned(response));
    }

    @Test
    void shouldPassWhenAvgCharsIsSufficient() {
        // Healthy text document e.g. 3 pages with 1466 chars/page (like the user's RCM PDF!)
        ExtractorResponse response = new ExtractorResponse(3, 4398, 1466.0, List.of());
        assertFalse(scannedCheckService.isScanned(response));
    }
}
