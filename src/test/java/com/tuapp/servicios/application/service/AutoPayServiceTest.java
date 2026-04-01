package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.CreateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.request.UpdateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.response.AutoPayRuleResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.AutoPayRuleMapper;
import com.tuapp.servicios.domain.enums.MetodoPago;
import com.tuapp.servicios.domain.enums.RolUsuario;
import com.tuapp.servicios.domain.model.*;
import com.tuapp.servicios.domain.repository.AutoPayRuleRepository;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import com.tuapp.servicios.domain.repository.ProviderCompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoPayServiceTest {

    @Mock private AutoPayRuleRepository autoPayRuleRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private ProviderCompanyRepository providerRepository;
    @Mock private PropertyService propertyService;
    @Mock private AutoPayRuleMapper autoPayRuleMapper;

    @InjectMocks
    private AutoPayService autoPayService;

    @Test
    void createRule_withValidRequest_returnsCreatedRule() {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        User user = User.builder().rol(RolUsuario.USER).activo(true).build();
        ReflectionTestUtils.setField(user, "id", userId);

        Property property = Property.builder().user(user).nombre("Casa").build();
        ReflectionTestUtils.setField(property, "id", propertyId);

        ProviderCompany provider = ProviderCompany.builder()
                .nombre("EPM").nit("900123456-1").activo(true).build();
        ReflectionTestUtils.setField(provider, "id", providerId);

        CreateAutoPayRuleRequest request = new CreateAutoPayRuleRequest();
        request.setPropertyId(propertyId);
        request.setProveedorId(providerId);
        request.setMetodoPago(MetodoPago.TARJETA_CREDITO);
        request.setDiasAntesVencimiento(3);
        request.setMontoMaximo(new BigDecimal("500000"));

        AutoPayRule savedRule = AutoPayRule.builder()
                .property(property).proveedor(provider)
                .metodoPago(MetodoPago.TARJETA_CREDITO)
                .diasAntesVencimiento(3)
                .montoMaximo(new BigDecimal("500000"))
                .activo(true).build();
        UUID ruleId = UUID.randomUUID();
        ReflectionTestUtils.setField(savedRule, "id", ruleId);

        AutoPayRuleResponse expectedResponse = AutoPayRuleResponse.builder()
                .id(ruleId).activo(true).build();

        doNothing().when(propertyService).validateOwnership(propertyId, userId);
        when(autoPayRuleRepository.existsByPropertyIdAndProveedorIdAndActivoTrueAndDeletedAtIsNull(
                propertyId, providerId)).thenReturn(false);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(autoPayRuleRepository.save(any())).thenReturn(savedRule);
        when(autoPayRuleMapper.toResponse(savedRule)).thenReturn(expectedResponse);

        AutoPayRuleResponse response = autoPayService.createRule(request, userId);

        assertThat(response.getId()).isEqualTo(ruleId);
        assertThat(response.getActivo()).isTrue();
        verify(autoPayRuleRepository).save(any());
    }

    @Test
    void createRule_withExistingActiveRule_throwsBusinessException() {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        CreateAutoPayRuleRequest request = new CreateAutoPayRuleRequest();
        request.setPropertyId(propertyId);
        request.setProveedorId(providerId);
        request.setMetodoPago(MetodoPago.PSE);
        request.setDiasAntesVencimiento(2);

        doNothing().when(propertyService).validateOwnership(propertyId, userId);
        when(autoPayRuleRepository.existsByPropertyIdAndProveedorIdAndActivoTrueAndDeletedAtIsNull(
                propertyId, providerId)).thenReturn(true);

        assertThatThrownBy(() -> autoPayService.createRule(request, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe una regla activa");

        verifyNoInteractions(propertyRepository, providerRepository);
        verify(autoPayRuleRepository, never()).save(any());
    }

    @Test
    void updateRule_withValidRequest_updatesFields() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", userId);
        Property property = Property.builder().user(user).build();
        ReflectionTestUtils.setField(property, "id", propertyId);

        AutoPayRule rule = AutoPayRule.builder()
                .property(property)
                .metodoPago(MetodoPago.PSE)
                .diasAntesVencimiento(2)
                .activo(true).build();
        ReflectionTestUtils.setField(rule, "id", ruleId);

        UpdateAutoPayRuleRequest updateRequest = new UpdateAutoPayRuleRequest();
        updateRequest.setDiasAntesVencimiento(5);
        updateRequest.setMontoMaximo(new BigDecimal("300000"));
        updateRequest.setActivo(false);

        AutoPayRuleResponse expectedResponse = AutoPayRuleResponse.builder()
                .id(ruleId).activo(false).build();

        when(autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)).thenReturn(Optional.of(rule));
        doNothing().when(propertyService).validateOwnership(propertyId, userId);
        when(autoPayRuleRepository.save(any())).thenReturn(rule);
        when(autoPayRuleMapper.toResponse(any())).thenReturn(expectedResponse);

        AutoPayRuleResponse response = autoPayService.updateRule(ruleId, updateRequest, userId);

        assertThat(rule.getDiasAntesVencimiento()).isEqualTo(5);
        assertThat(rule.getMontoMaximo()).isEqualByComparingTo(new BigDecimal("300000"));
        assertThat(rule.getActivo()).isFalse();
        assertThat(response.getId()).isEqualTo(ruleId);
    }

    @Test
    void updateRule_withNonExistentRule_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        when(autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autoPayService.updateRule(ruleId, new UpdateAutoPayRuleRequest(), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRule_withValidRule_softDeletesRule() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", userId);
        Property property = Property.builder().user(user).build();
        ReflectionTestUtils.setField(property, "id", propertyId);

        AutoPayRule rule = AutoPayRule.builder()
                .property(property).activo(true).build();
        ReflectionTestUtils.setField(rule, "id", ruleId);

        when(autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)).thenReturn(Optional.of(rule));
        doNothing().when(propertyService).validateOwnership(propertyId, userId);
        when(autoPayRuleRepository.save(any())).thenReturn(rule);

        autoPayService.deleteRule(ruleId, userId);

        assertThat(rule.getDeletedAt()).isNotNull();
        verify(autoPayRuleRepository).save(rule);
    }

    @Test
    void deleteRule_withNonExistentRule_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        when(autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autoPayService.deleteRule(ruleId, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(autoPayRuleRepository, never()).save(any());
    }
}
