package com.tuapp.servicios.domain.model;

import com.tuapp.servicios.domain.enums.ResultadoAudit;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_log_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_log_created_at", columnList = "created_at")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "accion", nullable = false, length = 100)
    private String accion;

    @Column(name = "entidad", length = 50)
    private String entidad;

    @Column(name = "entidad_id")
    private UUID entidadId;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 10)
    private ResultadoAudit resultado;

    @Column(name = "detalle", length = 500)
    private String detalle;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
