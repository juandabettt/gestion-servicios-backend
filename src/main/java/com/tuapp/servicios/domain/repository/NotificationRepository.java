package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByFacturaIdAndTipo(UUID facturaId, String tipo);

    Page<Notification> findByUsuarioIdAndLeidaFalseOrderByCreatedAtDesc(UUID usuarioId, Pageable pageable);

    Page<Notification> findByUsuarioIdOrderByCreatedAtDesc(UUID usuarioId, Pageable pageable);

    List<Notification> findByUsuarioIdAndLeidaFalse(UUID usuarioId);
}
