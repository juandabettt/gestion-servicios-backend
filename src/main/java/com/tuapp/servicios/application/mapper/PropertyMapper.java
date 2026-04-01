package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.PropertyResponse;
import com.tuapp.servicios.domain.model.Property;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PropertyMapper {
    PropertyResponse toResponse(Property property);
}
