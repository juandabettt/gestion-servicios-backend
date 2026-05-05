package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.CreateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.request.UpdateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.response.AutoPayRuleResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.AutoPayRuleMapper;
import com.tuapp.servicios.domain.enums.RolUsuario;
import com.tuapp.servicios.domain.model.AutoPayRule;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.repository.AutoPayRuleRepository;
import com.tuapp.servicios.domain.repository.UserRepository;
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
    @Mock private UserRepository userRepository;
    @Mock private AutoPayRuleMapper autoPayRuleMapper;

    @InjectMocks
    private AutoPayService autoPayService;

    @Test
    void createRule_withValidRequest_returnsCreatedRule() {
        UUID userId = UUID.randomUUID();

        User user = User.builder().rol(RolUsuario.USER).activo(true).build();
        ReflectionTestUtils.setField(user, "id", userId);

        CreateAutoPayRuleRequest request = new CreateAutoPayRuleRequest();
        request.setNombre("Pagar luz automáticamente");
        request.setTipoServicio("ENERGIA");
        request.setDiasAntesVencimiento(3);
        request.setMontoMaximo(new BigDecimal("500000"));

        AutoPayRule savedRule = AutoPayRule.builder()
                .usuario(user)
                .nombre("Pagar luz automáticamente")
                .tipoServicio("ENERGIA")
                .diasAntesVencimiento(3)
                .montoMaximo(new BigDecimal("500000"))
                .activa(true)
                .totalPagosRealizados(0)
                .build();
        UUID ruleId = UUID.randomUUID();
        ReflectionTestUtils.setField(savedRule, "id", ruleId);

        AutoPayRuleResponse expectedResponse = AutoPayRuleResponse.builder()
                .id(ruleId).activa(true).build();

        when(autoPayRuleRepository.existsByUsuarioIdAndNombreIgnoreCaseAndActivaTrueAndDeletedAtIsNull(
                userId, "Pagar luz automáticamente")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(autoPayRuleRepository.save(any())).thenReturn(savedRule);
        when(autoPayRuleMapper.toResponse(savedRule)).thenReturn(expectedResponse);

        AutoPayRuleResponse response = autoPayService.createRule(request, userId);

        assertThat(response.getId()).isEqualTo(ruleId);
        assertThat(response.isActiva()).isTrue();
        verify(autoPayRuleRepository).save(any());
    }

    @Test
    void createRule_withDuplicateName_throwsBusinessException() {
        UUID userId = UUID.randomUUID();

        CreateAutoPayRuleRequest request = new CreateAutoPayRuleRequest();
        request.setNombre("Pagar agua");
        request.setTipoServicio("AGUA");
        request.setDiasAntesVencimiento(2);

        when(autoPayRuleRepository.existsByUsuarioIdAndNombreIgnoreCaseAndActivaTrueAndDeletedAtIsNull(
                userId, "Pagar agua")).thenReturn(true);

        assertThatThrownBy(() -> autoPayService.createRule(request, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe una regla activa con ese nombre");

        verifyNoInteractions(userRepository);
        verify(autoPayRuleRepository, never()).save(any());
    }

    @Test
    void updateRule_withValidRequest_updatesFields() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", userId);

        AutoPayRule rule = AutoPayRule.builder()
                .usuario(user)
                .nombre("Pagar gas")
                .tipoServicio("GAS")
                .diasAntesVencimiento(2)
                .activa(true)
                .totalPagosRealizados(0)
                .build();
        ReflectionTestUtils.setField(rule, "id", ruleId);

        UpdateAutoPayRuleRequest updateRequest = new UpdateAutoPayRuleRequest();
        updateRequest.setDiasAntesVencimiento(5);
        updateRequest.setMontoMaximo(new BigDecimal("300000"));
        updateRequest.setActiva(false);

        AutoPayRuleResponse expectedResponse = AutoPayRuleResponse.builder()
                .id(ruleId).activa(false).build();

        when(autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)).thenReturn(Optional.of(rule));
        when(autoPayRuleRepository.save(any())).thenReturn(rule);
        when(autoPayRuleMapper.toResponse(any())).thenReturn(expectedResponse);

        AutoPayRuleResponse response = autoPayService.updateRule(ruleId, updateRequest, userId);

        assertThat(rule.getDiasAntesVencimiento()).isEqualTo(5);
        assertThat(rule.getMontoMaximo()).isEqualByComparingTo(new BigDecimal("300000"));
        assertThat(rule.isActiva()).isFalse();
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
    void updateRule_byDifferentUser_throwsBusinessException() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        User owner = User.builder().build();
        ReflectionTestUtils.setField(owner, "id", ownerId);

        AutoPayRule rule = AutoPayRule.builder()
                .usuario(owner).nombre("Pagar internet").tipoServicio("INTERNET")
                .diasAntesVencimiento(3).activa(true).totalPagosRealizados(0).build();
        ReflectionTestUtils.setField(rule, "id", ruleId);

        when(autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> autoPayService.updateRule(ruleId, new UpdateAutoPayRuleRequest(), attackerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No tienes permiso");
    }

    @Test
    void deleteRule_withValidRule_softDeletesRule() {
        UUID userId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", userId);

        AutoPayRule rule = AutoPayRule.builder()
                .usuario(user).nombre("Pagar todos").tipoServicio("TODOS")
                .diasAntesVencimiento(3).activa(true).totalPagosRealizados(0).build();
        ReflectionTestUtils.setField(rule, "id", ruleId);

        when(autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)).thenReturn(Optional.of(rule));
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
