package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.enums.EstadoJob;
import com.tuapp.servicios.domain.model.JobQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobQueueRepository extends JpaRepository<JobQueue, UUID> {

    @Query(value = "SELECT * FROM job_queue WHERE estado = 'PENDIENTE' AND (proximo_intento IS NULL OR proximo_intento <= :ahora) ORDER BY created_at ASC LIMIT :limite FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<JobQueue> findPendientesForUpdate(@Param("ahora") LocalDateTime ahora, @Param("limite") int limite);

    long countByEstado(EstadoJob estado);

    @Query("SELECT j FROM JobQueue j WHERE j.estado = 'EN_DLQ' AND j.createdAt >= :desde")
    List<JobQueue> findDlqJobs(@Param("desde") LocalDateTime desde);
}
