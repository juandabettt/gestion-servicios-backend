package com.tuapp.servicios.application.port.dto;

import com.tuapp.servicios.domain.enums.TipoServicio;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class ConsumptionHistoryContext {
    private UUID propertyId;
    private String ciudad;
    private TipoServicio tipoServicio;
    private List<ConsumoMensual> historial;
    private BigDecimal presupuestoMensual;
    private BigDecimal consumoPromedioVecinos;

    @Data @Builder
    public static class ConsumoMensual {
        private String periodo;
        private BigDecimal consumoUnidad;
        private BigDecimal montoTotal;
    }
}
