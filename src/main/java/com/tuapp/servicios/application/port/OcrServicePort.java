package com.tuapp.servicios.application.port;

import com.tuapp.servicios.application.port.dto.OcrExtractionResult;

public interface OcrServicePort {
    OcrExtractionResult extractInvoiceData(byte[] imageBytes, String mimeType);
}
