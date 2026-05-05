package com.tuapp.servicios.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_pay_rules",
    indexes = {
        @Index(name = "idx_auto_pay_rules_usuario", columnList = "usuario_id")
    })
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AutoPayRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "tipo_servicio", nullable = false, length = 50)
    @Builder.Default
    private String tipoServicio = "TODOS";

    @Column(name = "dias_antes_vencimiento", nullable = false)
    @Builder.Default
    private Integer diasAntesVencimiento = 3;

    @Column(name = "activa", nullable = false)
    @Builder.Default
    private boolean activa = true;

    @Column(name = "monto_maximo", precision = 19, scale = 4)
    private BigDecimal montoMaximo;

    @Column(name = "ultima_ejecucion")
    private LocalDateTime ultimaEjecucion;

    @Column(name = "total_pagos_realizados", nullable = false)
    @Builder.Default
    private Integer totalPagosRealizados = 0;
}
