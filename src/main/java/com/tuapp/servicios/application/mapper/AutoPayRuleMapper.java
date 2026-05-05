package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.AutoPayRuleResponse;
import com.tuapp.servicios.domain.model.AutoPayRule;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AutoPayRuleMapper {
    AutoPayRuleResponse toResponse(AutoPayRule rule);
}
