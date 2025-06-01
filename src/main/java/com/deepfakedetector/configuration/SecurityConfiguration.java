package com.deepfakedetector.configuration;


import com.deepfakedetector.exception.AdvancedAccessDeniedHandler;
import com.deepfakedetector.exception.CustomAuthenticationEntryPoint;
import com.deepfakedetector.repository.UserRepository;
import com.deepfakedetector.security.jwt.JWTConfigurer;
import com.deepfakedetector.security.jwt.JWTFilter;
import com.deepfakedetector.security.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableScheduling
@EnableCaching
@EnableJpaAuditing
public class SecurityConfiguration {

   private final TokenProvider tokenProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/activate", "/api/v1/auth/authenticate", "/api/v1/auth/account/reset-password/init", "/api/v1/auth/account/reset-password/check", "/api/v1/auth/account/reset-password/finish")
                        .permitAll().requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/auth/admin/**", "/api/v1//admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/users/**", "/api/v1/auth/**", "/api/v1/profile/**").hasRole("USER")
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/activate", "/api/v1/auth/authenticate").permitAll()
                        .anyRequest().authenticated())
                 .addFilterBefore(new JWTFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                        .accessDeniedHandler(new AdvancedAccessDeniedHandler())
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                UserRepository.USERS_BY_USER_NAME_CACHE,
                UserRepository.USERS_BY_EMAIL_CACHE
        );
    }
}


