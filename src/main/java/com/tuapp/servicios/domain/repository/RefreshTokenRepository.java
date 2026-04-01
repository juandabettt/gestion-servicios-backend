package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.enums.EstadoRefreshToken;
import com.tuapp.servicios.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenAndEstado(String token, EstadoRefreshToken estado);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.estado = 'REVOCADO' WHERE r.user.id = :userId AND r.estado = 'ACTIVO'")
    void revokeAllByUserId(@Param("userId") UUID userId);

    Optional<RefreshToken> findFirstByUserIdAndEstadoOrderByCreatedAtDesc(UUID userId, EstadoRefreshToken estado);
}
