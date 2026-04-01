package com.tuapp.servicios.domain.model;

import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import com.tuapp.servicios.domain.enums.MetodoPago;
import com.tuapp.servicios.infrastructure.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", nullable = false)
    private Invoice factura;

    @Column(name = "monto_transaccion", nullable = false, precision = 19, scale = 4)
    private BigDecimal montoTransaccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago;

    @Column(name = "banco_origen", length = 100)
    private String bancoOrigen;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "id_transaccion_pasarela", length = 500)
    private String idTransaccionPasarela;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_transaccion", nullable = false, length = 20)
    @Builder.Default
    private EstadoTransaccion estadoTransaccion = EstadoTransaccion.INICIADA;

    @Column(name = "url_redireccion_pse", length = 500)
    private String urlRedireccionPse;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    @Column(name = "intentos_webhook")
    @Builder.Default
    private Integer intentosWebhook = 0;

    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey;
}
