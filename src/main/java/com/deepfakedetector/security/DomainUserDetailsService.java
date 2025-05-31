package com.deepfakedetector.security;


import com.deepfakedetector.exception.UserNotActivatedException;
import com.deepfakedetector.model.entity.User;
import com.deepfakedetector.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;


@Component("userDetailsService")
@RequiredArgsConstructor
@Log4j2
public class DomainUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String userName) {
        log.debug("Authenticating user: {}", userName);

        // Check if the userName is an email
        if (new EmailValidator().isValid(userName, null)) {
            return repository.findOneWithAuthorityByEmailIgnoreCase(userName)
                    .map(user -> createSpringSecurityUser(userName, user))
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "User with email " + userName + " was not found in the database."));
        }

        // Fallback to userName name
        String lowercaseUserName = userName.toLowerCase(Locale.ENGLISH);
        return repository.findOneWithAuthorityByUserName(lowercaseUserName)
                .map(user -> createSpringSecurityUser(lowercaseUserName, user))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User " + lowercaseUserName + " was not found in the database."));
    }


    private org.springframework.security.core.userdetails.User createSpringSecurityUser(String userName, User user) {
        if (!user.isActivated()) {
            throw new UserNotActivatedException("User " + userName + " is not activated.");
        }

        // Map authorities
        List<GrantedAuthority> authorities = user.getAuthorities().stream()
                .map(authority -> new SimpleGrantedAuthority(authority.getName()))
                .collect(Collectors.toList());

        // Return a Spring Security User object
        return new org.springframework.security.core.userdetails.User(user.getUserName(), user.getPassword(), authorities);
    }
}
