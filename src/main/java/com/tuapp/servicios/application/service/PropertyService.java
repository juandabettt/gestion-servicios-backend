package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.CreatePropertyRequest;
import com.tuapp.servicios.application.dto.request.UpdatePropertyRequest;
import com.tuapp.servicios.application.dto.response.PropertyResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.exception.UnauthorizedAccessException;
import com.tuapp.servicios.application.mapper.PropertyMapper;
import com.tuapp.servicios.domain.model.Property;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import com.tuapp.servicios.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final PropertyMapper propertyMapper;

    @Transactional
    public PropertyResponse create(CreatePropertyRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        Property property = Property.builder()
                .user(user)
                .nombre(request.getNombre())
                .direccion(request.getDireccion())
                .ciudad(request.getCiudad())
                .esPrincipal(Boolean.TRUE.equals(request.getEsPrincipal()))
                .build();
        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    @Transactional(readOnly = true)
    public Page<PropertyResponse> listByUser(UUID userId, Pageable pageable) {
        return propertyRepository.findByUserIdAndDeletedAtIsNull(userId, pageable)
                .map(propertyMapper::toResponse);
    }

    @Transactional
    public PropertyResponse update(UUID propertyId, UpdatePropertyRequest request, UUID userId) {
        Property property = propertyRepository.findByIdAndUserIdAndDeletedAtIsNull(propertyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad", propertyId));
        if (request.getNombre() != null) property.setNombre(request.getNombre());
        if (request.getDireccion() != null) property.setDireccion(request.getDireccion());
        if (request.getCiudad() != null) property.setCiudad(request.getCiudad());
        if (request.getEsPrincipal() != null) property.setEsPrincipal(request.getEsPrincipal());
        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    @Transactional
    public void delete(UUID propertyId, UUID userId) {
        Property property = propertyRepository.findByIdAndUserIdAndDeletedAtIsNull(propertyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad", propertyId));
        property.softDelete();
        propertyRepository.save(property);
    }

    public void validateOwnership(UUID propertyId, UUID userId) {
        if (!propertyRepository.existsByIdAndUserId(propertyId, userId)) {
            throw new UnauthorizedAccessException("No tienes permiso para acceder a esta propiedad");
        }
    }
}
