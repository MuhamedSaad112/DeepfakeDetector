package com.deepfakedetector.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entity class representing a User in the system.
 */
@Entity
@Table(name = "sec_user")
@Getter
@Setter
@NoArgsConstructor
public class User extends AbstractAuditingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    // Primary Key
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // userName Field
    @NotNull(message = "userName cannot be Null.")
    @Size(min = 5, max = 50, message = "userName must be between 5 and 50 characters.")
    // @Pattern(regexp = Validation.USER_NAME_PATTERN, message = "userName must be alphanumeric and between 5 to 50 characters.")
    @Column(length = 50, unique = true, nullable = false)
    private String userName;

    // Password Field
    @JsonIgnore
    @NotNull(message = "Password cannot be null.")
    @Size(min = 10, max = 100, message = "Password must be between 10 and 100 characters.")
    //  @Pattern(regexp = Validation.PASSWORD_PATTERN, message = "Password must contain at least one lowercase, one uppercase, one digit, one special character, and be between 10 and 100 characters long.")
    @Column(name = "password_hash", length = 60, nullable = false)
    private String password;

    // Personal Information
    @Size(max = 50, message = "First name must not exceed 50 characters.")
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters.")
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Email(message = "Invalid email format.")
    @Size(min = 5, max = 254, message = "Email must be between 5 and 254 characters.")
    @Column(length = 254, unique = true)
    private String email;

    // Language Key
    @Size(min = 2, max = 10, message = "Language key must be between 2 and 10 characters.")
    @Column(name = "lang_key", length = 10)
    private String langKey;

    // Activation Status
    @NotNull(message = "Activation status cannot be null.")
    @Column(nullable = false)
    private boolean activated = false;

    // Optional Fields
    @Size(max = 256, message = "Image URL must not exceed 256 characters.")
    @Column(name = "image_url", length = 256)
    private String imageUrl;

    @Size(max = 20, message = "Activation key must not exceed 20 characters.")
    @Column(name = "activation_key", length = 20)
    @JsonIgnore
    private String activationKey;

    @Size(max = 20, message = "Reset key must not exceed 20 characters.")
    @Column(name = "reset_key", length = 20)
    @JsonIgnore
    private String resetKey;

    @Column(name = "reset_date")
    private Instant resetDate = null;

    // Relationships
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sec_user_authority",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "authority_name", referencedColumnName = "name")
    )
    @BatchSize(size = 20)
    private Set<Authority> authorities = new HashSet<>();


    public void setUserName(String userName) {
        this.userName = userName == null ? null : userName.toLowerCase();
    }
}
