package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.CreatePropertyRequest;
import com.tuapp.servicios.application.dto.response.PropertyResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.exception.UnauthorizedAccessException;
import com.tuapp.servicios.application.mapper.PropertyMapper;
import com.tuapp.servicios.domain.model.Property;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import com.tuapp.servicios.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PropertyMapper propertyMapper;

    @InjectMocks
    private PropertyService propertyService;

    @Test
    void create_withValidUser_returnsPropertyResponse() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().nombre("Test").email("test@test.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setNombre("Casa principal");
        request.setCiudad("Pasto");
        request.setEsPrincipal(true);

        Property savedProperty = Property.builder().user(user).nombre("Casa principal").build();
        ReflectionTestUtils.setField(savedProperty, "id", UUID.randomUUID());

        PropertyResponse expectedResponse = PropertyResponse.builder()
                .nombre("Casa principal").ciudad("Pasto").esPrincipal(true).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(propertyRepository.save(any())).thenReturn(savedProperty);
        when(propertyMapper.toResponse(any())).thenReturn(expectedResponse);

        PropertyResponse result = propertyService.create(request, userId);

        assertThat(result.getNombre()).isEqualTo("Casa principal");
        verify(propertyRepository).save(any(Property.class));
    }

    @Test
    void create_withInvalidUser_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.create(new CreatePropertyRequest(), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validateOwnership_withValidOwner_doesNotThrow() {
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(propertyRepository.existsByIdAndUserId(propertyId, userId)).thenReturn(true);

        assertThatCode(() -> propertyService.validateOwnership(propertyId, userId))
                .doesNotThrowAnyException();
    }

    @Test
    void validateOwnership_withDifferentOwner_throwsUnauthorized() {
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(propertyRepository.existsByIdAndUserId(propertyId, userId)).thenReturn(false);

        assertThatThrownBy(() -> propertyService.validateOwnership(propertyId, userId))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void delete_withValidOwner_softDeletesProperty() {
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Property property = Property.builder().nombre("Test").build();

        when(propertyRepository.findByIdAndUserIdAndDeletedAtIsNull(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(propertyRepository.save(any())).thenReturn(property);

        propertyService.delete(propertyId, userId);

        assertThat(property.getDeletedAt()).isNotNull();
        verify(propertyRepository).save(property);
    }
}
