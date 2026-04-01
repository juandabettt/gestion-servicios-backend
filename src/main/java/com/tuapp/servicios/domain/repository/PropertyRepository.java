package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.model.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {
    Page<Property> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);
    Optional<Property> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    boolean existsByIdAndUserId(UUID id, UUID userId);
}
