package com.ttnails.booking_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;


import java.util.Date;

@Entity
@NoArgsConstructor
@Setter @Getter
@AllArgsConstructor
public class InvalidToken {
    @Id
    String id;
    Date time;
}
