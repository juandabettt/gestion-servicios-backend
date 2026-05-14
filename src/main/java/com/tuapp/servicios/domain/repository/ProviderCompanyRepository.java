package com.tuapp.servicios.domain.repository;

import com.tuapp.servicios.domain.enums.TipoServicio;
import com.tuapp.servicios.domain.model.ProviderCompany;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderCompanyRepository extends JpaRepository<ProviderCompany, UUID> {
    Page<ProviderCompany> findByActivoTrue(Pageable pageable);
    List<ProviderCompany> findByTipoServicioAndActivoTrue(TipoServicio tipoServicio);
    boolean existsByNit(String nit);
    Optional<ProviderCompany> findByNombreContainingIgnoreCase(String nombre);
}
