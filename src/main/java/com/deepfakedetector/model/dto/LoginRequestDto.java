package com.deepfakedetector.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class LoginRequestDto {


    @NotNull(message = "Username must not be null.")
    @NotBlank(message = "Username must not be blank.")
    @Size(min = 5, max = 50, message = "Username must be between 5 and 50 characters.")
    private String userName;


    @NotNull(message = "Password must not be null.")
    @NotBlank(message = "Password must not be blank.")
    @Size(min = 5, max = 100, message = "Password must be between 5 and 100 characters.")
    private String password;


    private boolean rememberMe = false;

}
