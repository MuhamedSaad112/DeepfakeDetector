package com.deepfakedetector.model.request;

import com.deepfakedetector.model.dto.AdminUserDTO;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class RegisterUserRequest extends AdminUserDTO {


    public static final int PASSWORD_MIN_LENGTH = 10;


    public static final int PASSWORD_MAX_LENGTH = 100;


    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH,
            message = "Password must be between " + PASSWORD_MIN_LENGTH + " and " + PASSWORD_MAX_LENGTH + " characters long.")
    private String password;

}
