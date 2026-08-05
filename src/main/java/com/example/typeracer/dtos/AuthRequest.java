package com.example.typeracer.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Username is required")
    @Size(max = 20, message = "Username must be at most 20 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 4, max = 72, message = "Password must be between 4 and 72 characters")
    private String password;

    @Email(message = "Email is invalid")
    private String email;
}
