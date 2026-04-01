package com.tuapp.servicios.domain.model;

import com.tuapp.servicios.domain.enums.MetodoPago;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPreferences extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "dias_anticipacion_alerta")
    @Builder.Default
    private Integer diasAnticipacionAlerta = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago_default", length = 20)
    private MetodoPago metodoPagoDefault;

    @Column(name = "presupuesto_mensual_agua", precision = 19, scale = 4)
    private BigDecimal presupuestoMensualAgua;

    @Column(name = "presupuesto_mensual_energia", precision = 19, scale = 4)
    private BigDecimal presupuestoMensualEnergia;

    @Column(name = "presupuesto_mensual_gas", precision = 19, scale = 4)
    private BigDecimal presupuestoMensualGas;

    @Column(name = "presupuesto_mensual_internet", precision = 19, scale = 4)
    private BigDecimal presupuestoMensualInternet;

    @Column(name = "notificaciones_email")
    @Builder.Default
    private Boolean notificacionesEmail = true;

    @Column(name = "notificaciones_push")
    @Builder.Default
    private Boolean notificacionesPush = true;

    @Column(name = "moneda", length = 3)
    @Builder.Default
    private String moneda = "COP";
}
