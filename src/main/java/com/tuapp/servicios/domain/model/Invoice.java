package com.tuapp.servicios.domain.model;

import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.infrastructure.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices",
    indexes = {
        @Index(name = "idx_invoices_property_estado", columnList = "property_id, estado"),
        @Index(name = "idx_invoices_vencimiento_estado", columnList = "fecha_vencimiento, estado"),
        @Index(name = "idx_invoices_proveedor_periodo", columnList = "proveedor_id, periodo_facturado")
    })
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Invoice extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = true)
    private ProviderCompany proveedor;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "numero_referencia", nullable = true, length = 500)
    private String numeroReferencia;

    @Column(name = "fecha_emision", nullable = true)
    private LocalDate fechaEmision;

    @Column(name = "fecha_vencimiento", nullable = true)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_total", nullable = true, precision = 19, scale = 4)
    private BigDecimal montoTotal;

    @Column(name = "consumo_unidad", precision = 19, scale = 4)
    private BigDecimal consumoUnidad;

    @Column(name = "unidad_medida", length = 20)
    private String unidadMedida;

    @Column(name = "periodo_facturado", length = 20)
    private String periodoFacturado;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private EstadoFactura estado = EstadoFactura.PROCESANDO_OCR;

    @Column(name = "url_foto_factura", length = 500)
    private String urlFotoFactura;

    @Column(name = "ocr_datos_raw", columnDefinition = "TEXT")
    private String ocrDatosRaw;

    @Column(name = "ocr_confianza", precision = 5, scale = 2)
    private BigDecimal ocrConfianza;

    @Column(name = "ingreso_manual")
    @Builder.Default
    private Boolean ingresoManual = false;
}
