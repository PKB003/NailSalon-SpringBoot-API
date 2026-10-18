package com.ttnails.booking_service.services.controller;

import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.services.dto.ServiceRequest;
import com.ttnails.booking_service.services.entity.Service;
import com.ttnails.booking_service.services.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/services")
public class ServiceController {
    @Autowired
    private ServiceService serviceService;

    @GetMapping
    public ApiResponse<List<Service>> getAllServices() {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), serviceService.getAllServices());
    }

    @PostMapping
    public ApiResponse<Service> createService(@RequestBody @Valid ServiceRequest request) {
        return new ApiResponse<>(201, "Service created", LocalDateTime.now(), serviceService.createService(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Service> updateService(@PathVariable Long id,
                                              @RequestBody @Valid ServiceRequest request) {
        return new ApiResponse<>(200, "Service updated", LocalDateTime.now(), serviceService.updateService(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteService(@PathVariable Long id) {
        serviceService.deleteService(id);
        return new ApiResponse<>(200, "Service deleted", LocalDateTime.now(), null);
    }
}
