package com.tuapp.servicios.application.port.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder
public class OcrExtractionResult {
    private String empresa;
    private String numeroReferencia;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private BigDecimal montoTotal;
    private BigDecimal consumoUnidad;
    private String unidadMedida;
    private String periodoFacturado;
    private BigDecimal confianza;
    private String datosRaw;
    private boolean exitoso;
    private String errorMensaje;
}
