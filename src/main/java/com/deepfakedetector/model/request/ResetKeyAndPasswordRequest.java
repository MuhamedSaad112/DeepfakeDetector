package com.deepfakedetector.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ResetKeyAndPasswordRequest {


    @NotBlank(message = "Reset key must not be blank.")
    private String key;


    @Size(min = 10, max = 100,
            message = "Password must be between 10 and 100 characters long.")
    private String newPassword;

}
