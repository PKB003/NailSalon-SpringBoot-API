package com.ttnails.booking_service.customers.controller;

import com.ttnails.booking_service.common.dto.ApiResponse;
import com.ttnails.booking_service.common.dto.PageResponse;
import com.ttnails.booking_service.customers.dto.CustomerDTO;
import com.ttnails.booking_service.customers.service.CustomerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * GET /customers — danh sách khách hàng (chỉ ADMIN).
     *
     * <p>Không có {@code page} → array cũ; có {@code page} → {@link PageResponse} (S2-09).</p>
     */
    @GetMapping
    public ApiResponse<?> getAllCustomers(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "20") int size) {
        if (page == null) {
            return new ApiResponse<>(200, "Success", LocalDateTime.now(),
                    customerService.getAllCustomers());
        }
        PageResponse<CustomerDTO> result = customerService.getCustomers(page, size);
        return new ApiResponse<>(200, "Success", LocalDateTime.now(), result);
    }
}
