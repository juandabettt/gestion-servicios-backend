package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.CreateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.request.UpdateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.response.AutoPayRuleResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.AutoPayRuleMapper;
import com.tuapp.servicios.domain.model.AutoPayRule;
import com.tuapp.servicios.domain.model.Property;
import com.tuapp.servicios.domain.model.ProviderCompany;
import com.tuapp.servicios.domain.repository.AutoPayRuleRepository;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import com.tuapp.servicios.domain.repository.ProviderCompanyRepository;
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
    private final PropertyRepository propertyRepository;
    private final ProviderCompanyRepository providerRepository;
    private final PropertyService propertyService;
    private final AutoPayRuleMapper autoPayRuleMapper;

    @Transactional
    public AutoPayRuleResponse createRule(CreateAutoPayRuleRequest request, UUID userId) {
        propertyService.validateOwnership(request.getPropertyId(), userId);
        if (autoPayRuleRepository.existsByPropertyIdAndProveedorIdAndActivoTrueAndDeletedAtIsNull(
                request.getPropertyId(), request.getProveedorId())) {
            throw new BusinessException("Ya existe una regla activa para este proveedor y propiedad");
        }
        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad", request.getPropertyId()));
        ProviderCompany provider = providerRepository.findById(request.getProveedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.getProveedorId()));
        AutoPayRule rule = AutoPayRule.builder()
                .property(property).proveedor(provider).metodoPago(request.getMetodoPago())
                .diasAntesVencimiento(request.getDiasAntesVencimiento())
                .montoMaximo(request.getMontoMaximo()).activo(true).build();
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
        propertyService.validateOwnership(rule.getProperty().getId(), userId);
        if (request.getMetodoPago() != null) rule.setMetodoPago(request.getMetodoPago());
        if (request.getDiasAntesVencimiento() != null) rule.setDiasAntesVencimiento(request.getDiasAntesVencimiento());
        if (request.getMontoMaximo() != null) rule.setMontoMaximo(request.getMontoMaximo());
        if (request.getActivo() != null) rule.setActivo(request.getActivo());
        return autoPayRuleMapper.toResponse(autoPayRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID ruleId, UUID userId) {
        AutoPayRule rule = autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Regla de autopago", ruleId));
        propertyService.validateOwnership(rule.getProperty().getId(), userId);
        rule.softDelete();
        autoPayRuleRepository.save(rule);
    }
}
