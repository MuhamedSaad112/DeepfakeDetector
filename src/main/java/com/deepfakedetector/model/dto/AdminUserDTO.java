package com.deepfakedetector.model.dto;


import com.deepfakedetector.model.entity.Authority;
import com.deepfakedetector.model.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AdminUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;


    private UUID id;


    @NotNull(message = "userName cannot be Null.")
    @Size(min = 5, max = 50)
    private String userName;

    @Size(max = 50)
    private String firstName;


    @Size(max = 50)
    private String lastName;

    @Email
    @Size(min = 10, max = 254)
    private String email;

    @Size(max = 255)
    private String imageUrl;

    private String createdBy;

    private Instant createdDate;

    private String langKey;

    private String lastModifiedBy;

    private Instant lastModifiedDate;


    private boolean activated = false;


    private Set<String> authorities;


    public AdminUserDTO(User user) {
        this.id = user.getId();
        this.userName = user.getUserName();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.langKey = user.getLangKey();
        this.email = user.getEmail();
        this.activated = user.isActivated();
        this.imageUrl = user.getImageUrl();
        this.createdBy = user.getCreatedBy();
        this.createdDate = user.getCreatedDate();
        this.lastModifiedBy = user.getLastModifiedBy();
        this.lastModifiedDate = user.getLastModifiedDate();
        this.authorities = user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet());
    }
}
