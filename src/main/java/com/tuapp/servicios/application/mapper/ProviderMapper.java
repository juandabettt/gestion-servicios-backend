package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.ProviderResponse;
import com.tuapp.servicios.domain.model.ProviderCompany;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProviderMapper {
    ProviderResponse toResponse(ProviderCompany provider);
}
