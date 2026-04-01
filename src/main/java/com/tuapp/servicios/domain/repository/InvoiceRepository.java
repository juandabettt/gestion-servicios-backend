package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.enums.TipoServicio;
import com.tuapp.servicios.domain.model.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("SELECT i FROM Invoice i JOIN i.property p WHERE p.user.id = :userId AND i.deletedAt IS NULL")
    Page<Invoice> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT i FROM Invoice i JOIN i.property p WHERE p.user.id = :userId AND i.estado = :estado AND i.deletedAt IS NULL")
    Page<Invoice> findByUserIdAndEstado(@Param("userId") UUID userId, @Param("estado") EstadoFactura estado, Pageable pageable);

    @Query("SELECT i FROM Invoice i JOIN i.property p WHERE p.user.id = :userId AND i.proveedor.tipoServicio = :tipoServicio AND i.deletedAt IS NULL")
    Page<Invoice> findByUserIdAndTipoServicio(@Param("userId") UUID userId, @Param("tipoServicio") TipoServicio tipoServicio, Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE i.property.id = :propertyId AND i.estado = 'PENDIENTE' AND i.fechaVencimiento <= :fechaLimite AND i.deletedAt IS NULL")
    List<Invoice> findPendientesByPropertyAndVencimiento(@Param("propertyId") UUID propertyId, @Param("fechaLimite") LocalDate fechaLimite);

    @Query("SELECT i FROM Invoice i WHERE i.estado = 'PENDIENTE' AND i.fechaVencimiento <= :fechaLimite AND i.deletedAt IS NULL")
    List<Invoice> findAllPendientesProximasVencer(@Param("fechaLimite") LocalDate fechaLimite);

    @Query("SELECT i FROM Invoice i WHERE i.estado = 'PENDIENTE' AND i.fechaVencimiento < :hoy AND i.deletedAt IS NULL")
    List<Invoice> findVencidas(@Param("hoy") LocalDate hoy);

    Optional<Invoice> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT i FROM Invoice i WHERE i.property.id = :propertyId AND i.proveedor.id = :proveedorId AND i.deletedAt IS NULL ORDER BY i.fechaEmision DESC")
    List<Invoice> findHistorialByPropertyAndProveedor(@Param("propertyId") UUID propertyId, @Param("proveedorId") UUID proveedorId, Pageable pageable);
}
