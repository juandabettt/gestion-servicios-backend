package com.tuapp.servicios.application.dto.response;

import com.tuapp.servicios.domain.enums.TipoServicio;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
@Schema(description = "Proveedor de servicios públicos")
public class ProviderResponse {
    private UUID id;
    private String nombre;
    private String nit;
    private TipoServicio tipoServicio;
    private String ciudadCobertura;
    private String urlPortal;
    private String telefonoSoporte;
    private Integer cicloFacturacionDias;
    private Boolean activo;
}
