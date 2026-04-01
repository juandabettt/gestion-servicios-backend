package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.UserPreferencesResponse;
import com.tuapp.servicios.domain.model.UserPreferences;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserPreferencesMapper {
    UserPreferencesResponse toResponse(UserPreferences preferences);
}
