package com.tuapp.servicios.application.service;

import com.tuapp.servicios.domain.enums.ResultadoAudit;
import com.tuapp.servicios.domain.model.AuditLog;
import com.tuapp.servicios.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String accion, String entidad, UUID entidadId,
                    ResultadoAudit resultado, String detalle) {
        try {
            AuditLog audit = AuditLog.builder()
                    .userId(userId)
                    .accion(accion)
                    .entidad(entidad)
                    .entidadId(entidadId)
                    .resultado(resultado)
                    .detalle(detalle)
                    .build();
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.error("Error guardando registro de auditoría");
        }
    }
}
