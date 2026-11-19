package com.ttnails.booking_service.services.repository;

import com.ttnails.booking_service.services.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByName(String name);

    Optional<Service> findByIdAndActiveTrue(Long id);

    List<Service> findByActiveTrueOrderByIdAsc();

    List<Service> findByIdInAndActiveTrue(Collection<Long> ids);

    Optional<Service> findFirstByActiveTrueOrderByDurationAsc();
}
