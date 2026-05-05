package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.model.AutoPayRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutoPayRuleRepository extends JpaRepository<AutoPayRule, UUID> {

    @Query("SELECT r FROM AutoPayRule r WHERE r.usuario.id = :userId AND r.deletedAt IS NULL")
    Page<AutoPayRule> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    List<AutoPayRule> findByActivaTrueAndDeletedAtIsNull();

    Optional<AutoPayRule> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByUsuarioIdAndNombreIgnoreCaseAndActivaTrueAndDeletedAtIsNull(UUID usuarioId, String nombre);
}
