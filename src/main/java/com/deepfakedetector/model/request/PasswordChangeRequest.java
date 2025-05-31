package com.deepfakedetector.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordChangeRequest {


    @NotBlank(message = "Current password must not be blank.")
    @Size(min = 10, max = 100, message = "Current password must be between 10 and 100 characters.")
    private String currentPassword;


    @NotBlank(message = "New password must not be blank.")
    @Size(min = 10, max = 100, message = "New password must be between 10 and 100 characters.")
    private String newPassword;

}
