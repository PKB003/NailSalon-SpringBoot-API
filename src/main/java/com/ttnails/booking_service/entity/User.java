package com.ttnails.booking_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ttnails.booking_service.enums.Gender;
import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDate;
import java.util.*;

@Entity
@Table (name = "user")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    @Column(unique = true)
    private String phone;
    private boolean isVerifiedPhone;
    @Column(unique = true)
    private String email;
    private boolean isVerifiedEmail;
    @JsonIgnore
    private String password;
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> roles = new HashSet<>();
    private LocalDate dob;
    private String address;
    @Enumerated(EnumType.STRING)
    private Gender gender;
    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<Booking> bookingList = new ArrayList<>();

}
