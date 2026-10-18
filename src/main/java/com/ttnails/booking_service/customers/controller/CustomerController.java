package com.ttnails.booking_service.customers.controller;

import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.customers.dto.CustomerDTO;
import com.ttnails.booking_service.customers.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    @Autowired
    private CustomerService customerService;

    /** GET /customers — danh sách khách hàng (chỉ ADMIN) */
    @GetMapping
    public ApiResponse<List<CustomerDTO>> getAllCustomers() {
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), customerService.getAllCustomers());
    }
}
