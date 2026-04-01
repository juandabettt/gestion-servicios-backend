package com.tuapp.servicios.domain.model;

import com.tuapp.servicios.domain.enums.TipoServicio;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "provider_companies")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderCompany extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "nit", unique = true, nullable = false, length = 20)
    private String nit;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_servicio", nullable = false, length = 20)
    private TipoServicio tipoServicio;

    @Column(name = "codigo_convenio_recaudo", nullable = false, length = 50)
    private String codigoConvenioRecaudo;

    @Column(name = "url_portal", length = 255)
    private String urlPortal;

    @Column(name = "telefono_soporte", length = 20)
    private String telefonoSoporte;

    @Column(name = "ciudad_cobertura", length = 100)
    private String ciudadCobertura;

    @Column(name = "ciclo_facturacion_dias")
    @Builder.Default
    private Integer cicloFacturacionDias = 30;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
