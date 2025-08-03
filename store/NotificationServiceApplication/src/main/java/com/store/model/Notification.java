package com.store.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Notification {
    @Id
    private String orderId;
    private String userEmail;
    private String message;
}

