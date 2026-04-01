package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import com.tuapp.servicios.domain.model.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.factura.property.user.id = :userId AND pt.deletedAt IS NULL")
    Page<PaymentTransaction> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.estadoTransaccion = :estado AND pt.createdAt >= :desde AND pt.createdAt <= :hasta AND pt.deletedAt IS NULL")
    List<PaymentTransaction> findByEstadoAndCreatedAtBetween(@Param("estado") EstadoTransaccion estado,
                                                              @Param("desde") LocalDateTime desde,
                                                              @Param("hasta") LocalDateTime hasta);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.estadoTransaccion = 'APROBADA' AND pt.factura.estado != 'PAGADA' AND pt.deletedAt IS NULL")
    List<PaymentTransaction> findAprobadaSinFacturaPagada();
}
