package com.tuapp.servicios.domain.model;

import com.tuapp.servicios.domain.enums.EstadoJob;
import com.tuapp.servicios.domain.enums.TipoJob;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_queue",
    indexes = {
        @Index(name = "idx_job_queue_estado_proximo", columnList = "estado, proximo_intento")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobQueue extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_job", nullable = false, length = 30)
    private TipoJob tipoJob;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoJob estado = EstadoJob.PENDIENTE;

    @Column(name = "intentos")
    @Builder.Default
    private Integer intentos = 0;

    @Column(name = "max_intentos")
    @Builder.Default
    private Integer maxIntentos = 3;

    @Column(name = "proximo_intento")
    private LocalDateTime proximoIntento;

    @Column(name = "error_detalle", columnDefinition = "TEXT")
    private String errorDetalle;

    @Column(name = "worker_id", length = 100)
    private String workerId;
}
