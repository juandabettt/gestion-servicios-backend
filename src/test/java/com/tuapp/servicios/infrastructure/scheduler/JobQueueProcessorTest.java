package com.tuapp.servicios.infrastructure.scheduler;

import com.tuapp.servicios.domain.enums.EstadoJob;
import com.tuapp.servicios.domain.enums.TipoJob;
import com.tuapp.servicios.domain.model.JobQueue;
import com.tuapp.servicios.domain.repository.JobQueueRepository;
import com.tuapp.servicios.infrastructure.scheduler.handler.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobQueueProcessorTest {

    @Mock private JobQueueRepository jobQueueRepository;
    @Mock private OcrJobHandler ocrJobHandler;
    @Mock private AiAnalysisJobHandler aiAnalysisJobHandler;
    @Mock private AutoPayJobHandler autoPayJobHandler;
    @Mock private NotificationJobHandler notificationJobHandler;
    @Mock private ReconciliationJobHandler reconciliationJobHandler;

    @InjectMocks
    private JobQueueProcessor jobQueueProcessor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobQueueProcessor, "instanceId", "test-instance-1");
    }

    private JobQueue buildJob(TipoJob tipoJob) {
        String payloadJson = "{\"invoiceId\":\"" + UUID.randomUUID() + "\"}";
        JobQueue job = JobQueue.builder()
                .tipoJob(tipoJob)
                .payload(payloadJson)
                .estado(EstadoJob.PENDIENTE)
                .intentos(0)
                .maxIntentos(3)
                .build();
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
        return job;
    }

    @Test
    void processPendingJobs_withOcrJob_dispatchesToOcrHandler() throws Exception {
        JobQueue job = buildJob(TipoJob.OCR_FACTURA);

        when(jobQueueRepository.findPendientesForUpdate(any(LocalDateTime.class), eq(5)))
                .thenReturn(List.of(job));
        doNothing().when(ocrJobHandler).handle(any());

        jobQueueProcessor.processPendingJobs();

        assertThat(job.getEstado()).isEqualTo(EstadoJob.COMPLETADO);
        assertThat(job.getWorkerId()).isEqualTo("test-instance-1");
        verify(ocrJobHandler).handle(job.getPayload());
        verify(jobQueueRepository, times(2)).save(job);
    }

    @Test
    void processPendingJobs_withAiAnalysisJob_dispatchesToAiHandler() throws Exception {
        JobQueue job = buildJob(TipoJob.ANALISIS_IA);

        when(jobQueueRepository.findPendientesForUpdate(any(), anyInt()))
                .thenReturn(List.of(job));
        doNothing().when(aiAnalysisJobHandler).handle(any());

        jobQueueProcessor.processPendingJobs();

        assertThat(job.getEstado()).isEqualTo(EstadoJob.COMPLETADO);
        verify(aiAnalysisJobHandler).handle(job.getPayload());
    }

    @Test
    void processPendingJobs_withFailingJob_retriesWithBackoff() throws Exception {
        JobQueue job = buildJob(TipoJob.OCR_FACTURA);
        job.setIntentos(0);

        when(jobQueueRepository.findPendientesForUpdate(any(), anyInt()))
                .thenReturn(List.of(job));
        doThrow(new RuntimeException("OCR service unavailable")).when(ocrJobHandler).handle(any());

        jobQueueProcessor.processPendingJobs();

        assertThat(job.getEstado()).isEqualTo(EstadoJob.PENDIENTE);
        assertThat(job.getIntentos()).isEqualTo(1);
        assertThat(job.getProximoIntento()).isAfter(LocalDateTime.now());
        assertThat(job.getErrorDetalle()).contains("OCR service unavailable");
        // backoff for attempt 1: Math.pow(5, 0) = 1 minute
        assertThat(job.getProximoIntento()).isBefore(LocalDateTime.now().plusMinutes(2));
    }

    @Test
    void processPendingJobs_withMaxAttemptsReached_movesToDlq() throws Exception {
        JobQueue job = buildJob(TipoJob.OCR_FACTURA);
        job.setIntentos(2); // one more failure will reach maxIntentos=3

        when(jobQueueRepository.findPendientesForUpdate(any(), anyInt()))
                .thenReturn(List.of(job));
        doThrow(new RuntimeException("Persistent failure")).when(ocrJobHandler).handle(any());

        jobQueueProcessor.processPendingJobs();

        assertThat(job.getEstado()).isEqualTo(EstadoJob.EN_DLQ);
        assertThat(job.getIntentos()).isEqualTo(3);
        assertThat(job.getErrorDetalle()).contains("MAX INTENTOS ALCANZADOS");
    }

    @Test
    void processPendingJobs_withNoJobs_doesNothing() {
        when(jobQueueRepository.findPendientesForUpdate(any(), anyInt()))
                .thenReturn(List.of());

        jobQueueProcessor.processPendingJobs();

        verify(jobQueueRepository, never()).save(any());
        verifyNoInteractions(ocrJobHandler, aiAnalysisJobHandler, autoPayJobHandler);
    }

    @Test
    void processPendingJobs_withAutoPayJob_dispatchesToAutoPayHandler() throws Exception {
        JobQueue job = buildJob(TipoJob.AUTOPAGO);

        when(jobQueueRepository.findPendientesForUpdate(any(), anyInt()))
                .thenReturn(List.of(job));
        doNothing().when(autoPayJobHandler).handle(any());

        jobQueueProcessor.processPendingJobs();

        assertThat(job.getEstado()).isEqualTo(EstadoJob.COMPLETADO);
        verify(autoPayJobHandler).handle(job.getPayload());
    }

    @Test
    void processPendingJobs_withNotificationJob_dispatchesToNotificationHandler() throws Exception {
        JobQueue job = buildJob(TipoJob.NOTIFICACION);

        when(jobQueueRepository.findPendientesForUpdate(any(), anyInt()))
                .thenReturn(List.of(job));
        doNothing().when(notificationJobHandler).handle(any());

        jobQueueProcessor.processPendingJobs();

        assertThat(job.getEstado()).isEqualTo(EstadoJob.COMPLETADO);
        verify(notificationJobHandler).handle(job.getPayload());
    }

    @Test
    void alertDeadLetterJobs_withDlqJobs_logsWarning() {
        when(jobQueueRepository.countByEstado(EstadoJob.EN_DLQ)).thenReturn(3L);

        // Should not throw; only logs
        jobQueueProcessor.alertDeadLetterJobs();

        verify(jobQueueRepository).countByEstado(EstadoJob.EN_DLQ);
    }

    @Test
    void alertDeadLetterJobs_withNoDlqJobs_doesNotLog() {
        when(jobQueueRepository.countByEstado(EstadoJob.EN_DLQ)).thenReturn(0L);

        jobQueueProcessor.alertDeadLetterJobs();

        verify(jobQueueRepository).countByEstado(EstadoJob.EN_DLQ);
    }
}
