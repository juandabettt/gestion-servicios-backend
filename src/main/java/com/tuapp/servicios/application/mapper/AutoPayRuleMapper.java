package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.AutoPayRuleResponse;
import com.tuapp.servicios.domain.model.AutoPayRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AutoPayRuleMapper {
    @Mapping(source = "property.id", target = "propertyId")
    @Mapping(source = "property.nombre", target = "propertyNombre")
    @Mapping(source = "proveedor.id", target = "proveedorId")
    @Mapping(source = "proveedor.nombre", target = "proveedorNombre")
    AutoPayRuleResponse toResponse(AutoPayRule rule);
}
