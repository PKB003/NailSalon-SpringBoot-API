package com.ttnails.booking_service.users.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;


import java.util.Date;

@Entity
@Table(name = "invalid_token")
@NoArgsConstructor
@Setter @Getter
@AllArgsConstructor
public class InvalidToken {
    @Id
    String id;
    Date time;
}
