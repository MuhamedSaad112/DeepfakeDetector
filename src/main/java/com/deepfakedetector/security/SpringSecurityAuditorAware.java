package com.deepfakedetector.security;

import com.deepfakedetector.configuration.Validation;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;


@Component
public class SpringSecurityAuditorAware implements AuditorAware<String> {


    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(SecurityUtils.getCurrentUserUserName().orElse(Validation.SYSTEM));
    }
}
