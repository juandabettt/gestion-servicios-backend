package com.tuapp.servicios.application.mapper;

import com.tuapp.servicios.application.dto.response.NotificationResponse;
import com.tuapp.servicios.domain.model.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);
}
