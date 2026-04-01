package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.CreateProviderRequest;
import com.tuapp.servicios.application.dto.response.ProviderResponse;
import com.tuapp.servicios.application.dto.response.UserAdminResponse;
import com.tuapp.servicios.application.exception.DuplicateResourceException;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.ProviderMapper;
import com.tuapp.servicios.application.mapper.UserMapper;
import com.tuapp.servicios.domain.enums.TipoJob;
import com.tuapp.servicios.domain.model.ProviderCompany;
import com.tuapp.servicios.domain.repository.AuditLogRepository;
import com.tuapp.servicios.domain.repository.ProviderCompanyRepository;
import com.tuapp.servicios.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ProviderCompanyRepository providerRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final JobQueueService jobQueueService;
    private final ProviderMapper providerMapper;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public Page<ProviderResponse> listProviders(Pageable pageable) {
        return providerRepository.findByActivoTrue(pageable).map(providerMapper::toResponse);
    }

    @Transactional
    public ProviderResponse createProvider(CreateProviderRequest request) {
        if (providerRepository.existsByNit(request.getNit())) {
            throw new DuplicateResourceException("Ya existe un proveedor con NIT: " + request.getNit());
        }
        ProviderCompany provider = ProviderCompany.builder()
                .nombre(request.getNombre()).nit(request.getNit())
                .tipoServicio(request.getTipoServicio())
                .codigoConvenioRecaudo(request.getCodigoConvenioRecaudo())
                .urlPortal(request.getUrlPortal()).telefonoSoporte(request.getTelefonoSoporte())
                .ciudadCobertura(request.getCiudadCobertura())
                .cicloFacturacionDias(request.getCicloFacturacionDias()).activo(true).build();
        return providerMapper.toResponse(providerRepository.save(provider));
    }

    @Transactional
    public ProviderResponse updateProvider(UUID providerId, CreateProviderRequest request) {
        ProviderCompany provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", providerId));
        if (request.getNombre() != null) provider.setNombre(request.getNombre());
        if (request.getTipoServicio() != null) provider.setTipoServicio(request.getTipoServicio());
        if (request.getCiudadCobertura() != null) provider.setCiudadCobertura(request.getCiudadCobertura());
        if (request.getTelefonoSoporte() != null) provider.setTelefonoSoporte(request.getTelefonoSoporte());
        if (request.getUrlPortal() != null) provider.setUrlPortal(request.getUrlPortal());
        return providerMapper.toResponse(providerRepository.save(provider));
    }

    @Transactional(readOnly = true)
    public Page<UserAdminResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public Page<com.tuapp.servicios.domain.model.AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    @Transactional
    public void runReconciliation() {
        jobQueueService.enqueue(TipoJob.CONCILIACION, Map.of("trigger", "manual", "timestamp", System.currentTimeMillis()));
    }
}
