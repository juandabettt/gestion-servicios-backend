package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.enums.EstadoNotificacion;
import com.tuapp.servicios.domain.model.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT n FROM NotificationLog n WHERE n.estado = 'PENDIENTE' AND n.intentos < 3 ORDER BY n.createdAt ASC")
    List<NotificationLog> findPendientesParaEnviar(Pageable pageable);

    long countByUserIdAndEstado(UUID userId, EstadoNotificacion estado);
}
