package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.enums.EstadoAnalisis;
import com.tuapp.servicios.domain.enums.TipoAnalisis;
import com.tuapp.servicios.domain.enums.TipoServicio;
import com.tuapp.servicios.domain.model.AiAnalysis;
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
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {

    Page<AiAnalysis> findByPropertyIdAndEstadoOrderByCreatedAtDesc(UUID propertyId, EstadoAnalisis estado, Pageable pageable);

    @Query("SELECT a FROM AiAnalysis a WHERE a.property.id = :propertyId AND a.tipoServicio = :tipoServicio AND a.createdAt >= :desde ORDER BY a.createdAt DESC")
    List<AiAnalysis> findRecentByPropertyAndServicio(@Param("propertyId") UUID propertyId,
                                                      @Param("tipoServicio") TipoServicio tipoServicio,
                                                      @Param("desde") LocalDateTime desde);

    Optional<AiAnalysis> findFirstByPropertyIdAndTipoServicioAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID propertyId, TipoServicio tipoServicio, LocalDateTime desde);

    Optional<AiAnalysis> findFirstByPropertyIdAndTipoServicioAndTipoAnalisisAndEstadoOrderByCreatedAtDesc(
            UUID propertyId,
            TipoServicio tipoServicio,
            TipoAnalisis tipoAnalisis,
            EstadoAnalisis estado);
}
