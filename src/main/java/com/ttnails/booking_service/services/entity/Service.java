package com.ttnails.booking_service.services.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Service {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Thời lượng (phút) */
    private Integer duration;

    /** Giá (EUR) */
    private Double price;

    private String kategorie;

    private String beliebtheit;
}
