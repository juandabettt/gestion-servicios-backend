package com.tuapp.servicios.infrastructure.adapter.ocr;

import com.tuapp.servicios.application.port.OcrServicePort;
import com.tuapp.servicios.application.port.dto.OcrExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Random;

@Component
@Profile("local")
@Slf4j
@RequiredArgsConstructor
public class MockOcrServiceAdapter implements OcrServicePort {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private static final String[] EMPRESAS = {
        "Aguas de Pasto", "Electricaribe", "Gases del Caribe", "Claro Colombia", "ETB"
    };
    private static final String[] PERIODOS = {"2025-01", "2025-02", "2025-03"};
    private static final String[] TIPOS_SERVICIO = {"ENERGIA", "AGUA", "GAS", "INTERNET"};

    @Override
    public OcrExtractionResult extractInvoiceData(byte[] imageBytes, String mimeType) {
        log.info("Mock OCR: procesando imagen de {} bytes", imageBytes.length);

        try {
            Thread.sleep(500 + random.nextInt(1000)); // simular latencia
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String empresa = EMPRESAS[random.nextInt(EMPRESAS.length)];
        String periodo = PERIODOS[random.nextInt(PERIODOS.length)];
        String tipoServicio = TIPOS_SERVICIO[random.nextInt(TIPOS_SERVICIO.length)];
        BigDecimal monto = BigDecimal.valueOf(50000 + random.nextInt(200000));
        BigDecimal consumo = BigDecimal.valueOf(50 + random.nextInt(300));
        LocalDate emision = LocalDate.now().minusMonths(1);
        LocalDate vencimiento = emision.plusDays(30);
        BigDecimal confianza = BigDecimal.valueOf(75 + random.nextInt(20));

        String datosRaw;
        try {
            datosRaw = objectMapper.writeValueAsString(Map.of(
                "empresa", empresa, "monto", monto, "periodo", periodo,
                "confianza", confianza, "fuente", "MOCK_OCR"
            ));
        } catch (Exception e) {
            datosRaw = "{\"fuente\":\"MOCK_OCR\"}";
        }

        return OcrExtractionResult.builder()
                .empresa(empresa)
                .tipoServicio(tipoServicio)
                .numeroReferencia("REF-" + random.nextInt(999999999))
                .fechaEmision(emision)
                .fechaVencimiento(vencimiento)
                .montoTotal(monto)
                .consumoUnidad(consumo)
                .unidadMedida("kWh")
                .periodoFacturado(periodo)
                .confianza(confianza)
                .datosRaw(datosRaw)
                .exitoso(true)
                .build();
    }
}
