package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.UserAdminResponse;
import com.tuapp.servicios.domain.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserAdminResponse toAdminResponse(User user);
}
