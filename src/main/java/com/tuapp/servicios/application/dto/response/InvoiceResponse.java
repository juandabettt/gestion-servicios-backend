package com.tuapp.servicios.application.dto.response;

import com.tuapp.servicios.domain.enums.EstadoFactura;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
@Schema(description = "Datos de una factura de servicio público")
public class InvoiceResponse {
    private UUID id;
    private UUID propertyId;
    private String propertyNombre;
    private UUID proveedorId;
    private String proveedorNombre;
    private String tipoServicio;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private BigDecimal montoTotal;
    private BigDecimal consumoUnidad;
    private String unidadMedida;
    private String periodoFacturado;
    private EstadoFactura estado;
    @Schema(description = "URL pre-firmada para ver la imagen de la factura (válida 15 min)")
    private String urlFotoFactura;
    private BigDecimal ocrConfianza;
    private Boolean ingresoManual;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
