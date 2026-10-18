package com.ttnails.booking_service.services.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceRequest {
    @NotBlank(message = "Name can't be blank")
    private String name;

    private String description;

    @NotNull(message = "Duration is required")
    @Min(value = 5, message = "Duration must be at least 5 minutes")
    private Integer duration;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be >= 0")
    private BigDecimal price;

    private String kategorie;

    private String beliebtheit;
}
