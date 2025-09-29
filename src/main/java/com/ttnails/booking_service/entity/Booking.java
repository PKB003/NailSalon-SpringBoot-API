package com.ttnails.booking_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(name = "booking")
@Getter
@Setter
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String service;
    private LocalDateTime preferredDateTime;
    private String note;
    @ManyToOne()
    @JoinColumn(name = "user_id", nullable = true)
    private User user;
    public Booking() {
    }

    public Booking(String firstName, String lastName, String email, String phone, String service, LocalDateTime preferredDateTime, String note){
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.service = service;
        this.preferredDateTime = preferredDateTime;
        this.note = note;

    }
}
