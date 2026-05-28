package com.tuapp.servicios.application.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.dto.response.AiAnalysisResponse;
import com.tuapp.servicios.application.port.dto.AiAnalysisPortResult;
import com.tuapp.servicios.application.port.dto.ConsumptionHistoryContext;
import com.tuapp.servicios.domain.model.AiAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class AiAnalysisMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(source = "property.id", target = "propertyId")
    @Mapping(target = "recomendaciones", ignore = true)
    @Mapping(target = "anomalias", ignore = true)
    @Mapping(target = "prediccion", ignore = true)
    @Mapping(target = "consumoHistorico", ignore = true)
    public abstract AiAnalysisResponse toResponse(AiAnalysis analysis);

    @AfterMapping
    protected void enrichFromJson(AiAnalysis analysis,
            @MappingTarget AiAnalysisResponse.AiAnalysisResponseBuilder builder) {
        if (analysis.getResultadoJson() != null) {
            try {
                AiAnalysisPortResult result = objectMapper.readValue(
                        analysis.getResultadoJson(), AiAnalysisPortResult.class);
                builder.recomendaciones(result.getRecomendaciones());
                builder.anomalias(result.getAnomalias());
                builder.prediccion(result.getPrediccionProximaFactura());
            } catch (Exception e) {
                log.warn("No se pudo deserializar resultado_json para análisis {}: {}",
                        analysis.getId(), e.getMessage());
            }
        }
        if (analysis.getDatosEntrada() != null) {
            try {
                ConsumptionHistoryContext ctx = objectMapper.readValue(
                        analysis.getDatosEntrada(), ConsumptionHistoryContext.class);
                builder.consumoHistorico(ctx.getHistorial());
            } catch (Exception e) {
                log.warn("No se pudo deserializar datos_entrada para análisis {}: {}",
                        analysis.getId(), e.getMessage());
            }
        }
    }
}
