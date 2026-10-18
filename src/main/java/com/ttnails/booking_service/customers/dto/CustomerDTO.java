package com.ttnails.booking_service.customers.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer shape cho trang Admin:
 * { id, name, telefon, email, letzterBesuch, besuche }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {
    private String id;
    private String name;
    private String telefon;
    private String email;
    private String letzterBesuch;
    private long besuche;
}
