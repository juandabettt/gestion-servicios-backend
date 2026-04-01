package com.tuapp.servicios.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.domain.enums.EstadoJob;
import com.tuapp.servicios.domain.enums.TipoJob;
import com.tuapp.servicios.domain.model.JobQueue;
import com.tuapp.servicios.domain.repository.JobQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobQueueService {

    private final JobQueueRepository jobQueueRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public JobQueue enqueue(TipoJob tipoJob, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando payload del job", e);
        }
        JobQueue job = JobQueue.builder()
                .tipoJob(tipoJob)
                .payload(payloadJson)
                .estado(EstadoJob.PENDIENTE)
                .proximoIntento(LocalDateTime.now())
                .build();
        return jobQueueRepository.save(job);
    }

    @Transactional
    public JobQueue enqueueWithDelay(TipoJob tipoJob, Map<String, Object> payload, LocalDateTime proximoIntento) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando payload del job", e);
        }
        JobQueue job = JobQueue.builder()
                .tipoJob(tipoJob)
                .payload(payloadJson)
                .estado(EstadoJob.PENDIENTE)
                .proximoIntento(proximoIntento)
                .build();
        return jobQueueRepository.save(job);
    }
}
