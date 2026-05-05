package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.CreateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.request.UpdateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.response.AutoPayRuleResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.AutoPayRuleMapper;
import com.tuapp.servicios.domain.model.AutoPayRule;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.repository.AutoPayRuleRepository;
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
public class AutoPayService {

    private final AutoPayRuleRepository autoPayRuleRepository;
    private final UserRepository userRepository;
    private final AutoPayRuleMapper autoPayRuleMapper;

    @Transactional
    public AutoPayRuleResponse createRule(CreateAutoPayRuleRequest request, UUID userId) {
        if (autoPayRuleRepository.existsByUsuarioIdAndNombreIgnoreCaseAndActivaTrueAndDeletedAtIsNull(
                userId, request.getNombre())) {
            throw new BusinessException("Ya existe una regla activa con ese nombre");
        }
        User usuario = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        String tipoServicio = request.getTipoServicio() != null ? request.getTipoServicio() : "TODOS";
        AutoPayRule rule = AutoPayRule.builder()
                .usuario(usuario)
                .nombre(request.getNombre())
                .tipoServicio(tipoServicio)
                .diasAntesVencimiento(request.getDiasAntesVencimiento())
                .montoMaximo(request.getMontoMaximo())
                .activa(true)
                .totalPagosRealizados(0)
                .build();
        return autoPayRuleMapper.toResponse(autoPayRuleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public Page<AutoPayRuleResponse> listByUser(UUID userId, Pageable pageable) {
        return autoPayRuleRepository.findByUserId(userId, pageable).map(autoPayRuleMapper::toResponse);
    }

    @Transactional
    public AutoPayRuleResponse updateRule(UUID ruleId, UpdateAutoPayRuleRequest request, UUID userId) {
        AutoPayRule rule = autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Regla de autopago", ruleId));
        if (!rule.getUsuario().getId().equals(userId)) {
            throw new BusinessException("No tienes permiso para modificar esta regla");
        }
        if (request.getNombre() != null) rule.setNombre(request.getNombre());
        if (request.getTipoServicio() != null) rule.setTipoServicio(request.getTipoServicio());
        if (request.getDiasAntesVencimiento() != null) rule.setDiasAntesVencimiento(request.getDiasAntesVencimiento());
        if (request.getMontoMaximo() != null) rule.setMontoMaximo(request.getMontoMaximo());
        if (request.getActiva() != null) rule.setActiva(request.getActiva());
        return autoPayRuleMapper.toResponse(autoPayRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID ruleId, UUID userId) {
        AutoPayRule rule = autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Regla de autopago", ruleId));
        if (!rule.getUsuario().getId().equals(userId)) {
            throw new BusinessException("No tienes permiso para eliminar esta regla");
        }
        rule.softDelete();
        autoPayRuleRepository.save(rule);
    }
}
