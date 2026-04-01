package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.AiAnalysisResponse;
import com.tuapp.servicios.domain.model.AiAnalysis;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AiAnalysisMapper {
    @Mapping(source = "property.id", target = "propertyId")
    AiAnalysisResponse toResponse(AiAnalysis analysis);
}
