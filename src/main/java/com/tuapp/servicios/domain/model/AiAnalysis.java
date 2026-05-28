package com.tuapp.servicios.domain.model;

import com.tuapp.servicios.domain.enums.EstadoAnalisis;
import com.tuapp.servicios.domain.enums.TipoAnalisis;
import com.tuapp.servicios.domain.enums.TipoServicio;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ai_analysis")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiAnalysis extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_analisis", nullable = false, length = 20)
    private TipoAnalisis tipoAnalisis;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_servicio", nullable = false, length = 20)
    private TipoServicio tipoServicio;

    @Column(name = "descripcion", columnDefinition = "TEXT", nullable = false)
    private String descripcion;

    @Column(name = "impacto_estimado", length = 255)
    private String impactoEstimado;

    @Column(name = "periodo_analizado", length = 20)
    private String periodoAnalizado;

    @Column(name = "datos_entrada", columnDefinition = "TEXT")
    private String datosEntrada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoAnalisis estado = EstadoAnalisis.PROCESANDO;

    @Column(name = "calificacion_usuario")
    private Integer calificacionUsuario;

    @Column(name = "resultado_json", columnDefinition = "TEXT")
    private String resultadoJson;
}
