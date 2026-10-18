package com.ttnails.booking_service.services.service;

import com.ttnails.booking_service.common.exception.NotFoundException;
import com.ttnails.booking_service.services.dto.ServiceRequest;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@org.springframework.stereotype.Service
public class ServiceService {
    @Autowired
    private ServiceRepository serviceRepository;

    public List<Service> getAllServices() {
        return serviceRepository.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Service createService(ServiceRequest request) {
        Service service = Service.builder()
                .name(request.getName())
                .description(request.getDescription())
                .duration(request.getDuration())
                .price(request.getPrice() != null ? request.getPrice().doubleValue() : null)
                .kategorie(request.getKategorie())
                .beliebtheit(request.getBeliebtheit())
                .build();
        return serviceRepository.save(service);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Service updateService(Long id, ServiceRequest request) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Service not found with id: " + id));
        service.setName(request.getName());
        service.setDescription(request.getDescription());
        service.setDuration(request.getDuration());
        service.setPrice(request.getPrice() != null ? request.getPrice().doubleValue() : null);
        service.setKategorie(request.getKategorie());
        service.setBeliebtheit(request.getBeliebtheit());
        return serviceRepository.save(service);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteService(Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new NotFoundException("Service not found with id: " + id);
        }
        serviceRepository.deleteById(id);
    }
}
