package com.deepfakedetector.model.dto;

import com.deepfakedetector.model.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
public class UserResponseDto {

    private UUID id;


    private String userName;

    public UserResponseDto(User user) {
        this.id = user.getId();
        this.userName = user.getUserName();
    }
}
