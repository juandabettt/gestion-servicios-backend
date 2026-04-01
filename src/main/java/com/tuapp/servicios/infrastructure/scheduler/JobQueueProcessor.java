package com.tuapp.servicios.infrastructure.scheduler;

import com.tuapp.servicios.domain.enums.EstadoJob;
import com.tuapp.servicios.domain.model.JobQueue;
import com.tuapp.servicios.domain.repository.JobQueueRepository;
import com.tuapp.servicios.infrastructure.scheduler.handler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobQueueProcessor {

    private final JobQueueRepository jobQueueRepository;
    private final OcrJobHandler ocrJobHandler;
    private final AiAnalysisJobHandler aiAnalysisJobHandler;
    private final AutoPayJobHandler autoPayJobHandler;
    private final NotificationJobHandler notificationJobHandler;
    private final ReconciliationJobHandler reconciliationJobHandler;

    @Value("${app.instance-id:instance-1}")
    private String instanceId;

    // Ejecutar cada 10 segundos
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void processPendingJobs() {
        List<JobQueue> jobs = jobQueueRepository.findPendientesForUpdate(LocalDateTime.now(), 5);
        if (jobs.isEmpty()) return;

        log.debug("Procesando {} jobs pendientes", jobs.size());

        for (JobQueue job : jobs) {
            job.setEstado(EstadoJob.EN_PROCESO);
            job.setWorkerId(instanceId);
            jobQueueRepository.save(job);

            try {
                dispatch(job);
                job.setEstado(EstadoJob.COMPLETADO);
                log.info("Job {} completado exitosamente (tipo: {})", job.getId(), job.getTipoJob());
            } catch (Exception e) {
                job.setIntentos(job.getIntentos() + 1);
                log.warn("Error en job {} (tipo: {}) — intento {}/{}: {}",
                        job.getId(), job.getTipoJob(), job.getIntentos(), job.getMaxIntentos(), e.getMessage());

                if (job.getIntentos() < job.getMaxIntentos()) {
                    job.setEstado(EstadoJob.PENDIENTE);
                    // Backoff exponencial: 1min → 5min → 30min
                    int minutosEspera = (int) Math.pow(5, job.getIntentos() - 1);
                    job.setProximoIntento(LocalDateTime.now().plusMinutes(minutosEspera));
                    job.setErrorDetalle(e.getMessage());
                } else {
                    job.setEstado(EstadoJob.EN_DLQ);
                    job.setErrorDetalle("MAX INTENTOS ALCANZADOS: " + e.getMessage());
                    log.error("Job {} movido a DLQ después de {} intentos", job.getId(), job.getIntentos());
                }
            }

            jobQueueRepository.save(job);
        }
    }

    // Alerta diaria de jobs en DLQ
    @Scheduled(cron = "0 0 6 * * *")
    public void alertDeadLetterJobs() {
        long dlqCount = jobQueueRepository.countByEstado(EstadoJob.EN_DLQ);
        if (dlqCount > 0) {
            log.warn("ALERTA DLQ: Hay {} jobs en Dead Letter Queue. Revisar urgentemente.", dlqCount);
        }
    }

    private void dispatch(JobQueue job) throws Exception {
        switch (job.getTipoJob()) {
            case OCR_FACTURA -> ocrJobHandler.handle(job.getPayload());
            case ANALISIS_IA -> aiAnalysisJobHandler.handle(job.getPayload());
            case AUTOPAGO -> autoPayJobHandler.handle(job.getPayload());
            case NOTIFICACION -> notificationJobHandler.handle(job.getPayload());
            case CONCILIACION -> reconciliationJobHandler.handle(job.getPayload());
            default -> throw new IllegalArgumentException("Tipo de job no soportado: " + job.getTipoJob());
        }
    }
}
