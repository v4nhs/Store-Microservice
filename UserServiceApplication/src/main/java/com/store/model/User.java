package com.store.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @NotBlank(message = "Username không được trống")
    private String username;
    @NotBlank(message = "Password không được trống")
    @Size(min = 4, message = "Password phải từ 4 kí tự trở lên")
    private String password;
    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được trống")
    private String email;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    @JsonBackReference
    private Role role;
}