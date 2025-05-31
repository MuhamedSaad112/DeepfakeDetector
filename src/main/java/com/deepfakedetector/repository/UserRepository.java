package com.deepfakedetector.repository;

import com.deepfakedetector.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Cache Names
    String USERS_BY_USER_NAME_CACHE = "usersByUserName";
    String USERS_BY_EMAIL_CACHE = "usersByEmail";

    Optional<User> findOneByActivationKey(String activationKey);

    List<User> findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(Instant dateTime);

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    Optional<User> findOneByUserName(String userName);


    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthorityByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "authorities")
    Optional<User> findOneWithAuthorityByUserName(String userName);

    Page<User> findAllByIdNotNullAndActivatedIsTrue(Pageable pageable);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.authorities WHERE u.id = :id")
    Optional<User> findByIdWithAuthorities(@Param("id") UUID id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.authorities")
    Page<User> findAllWithAuthorities(Pageable pageable);
}
