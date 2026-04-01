package com.tuapp.servicios.domain.model;

import com.tuapp.servicios.domain.enums.CanalNotificacion;
import com.tuapp.servicios.domain.enums.EstadoNotificacion;
import com.tuapp.servicios.domain.enums.TipoNotificacion;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 10)
    private CanalNotificacion canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    @Builder.Default
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    @Column(name = "asunto", length = 255)
    private String asunto;

    @Column(name = "cuerpo_resumen", length = 500)
    private String cuerpoResumen;

    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Column(name = "intentos")
    @Builder.Default
    private Integer intentos = 0;
}
