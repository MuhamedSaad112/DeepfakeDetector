package com.deepfakedetector.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Component
@Log4j2
public class TokenProvider {

    private static final String AUTHORITIES_KEY = "auth";

    private static final String INVALID_JWT_TOKEN = "Invalid JWT token.";

    private final Key key;

    private final JwtParser jwtParser;

    private final SecurityMetersService metersService;

    private final long tokenValidityInMilliseconds;

    private final long tokenValidityInMillisecondsForRememberMe;

    public TokenProvider(@Value("${security.authentication.jwt.base64-secret}") String base64Secret,
                         @Value("${security.authentication.jwt.secret}") String secret,
                         @Value("${security.authentication.jwt.token-validity-in-seconds}") long tokenValidityInMilliseconds,
                         @Value("${security.authentication.jwt.token-validity-in-seconds-for-remember-me}") long tokenValidityInMillisecondsForRememberMe,
                         SecurityMetersService metersService) {

        byte[] keyBytes;

        if (base64Secret != null && !base64Secret.isEmpty()) {
            log.debug("Using a Base64-encoded JWT secret key.");
            keyBytes = Base64.getDecoder().decode(base64Secret);
        } else if (secret != null && !secret.isEmpty()) {
            log.warn("Using plain  secret key.");
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        } else {
            throw new IllegalStateException(
                    "No JWT secret configured. Please configure either 'security.authentication.jwt.secret' or 'security.authentication.jwt.base64-secret'.");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parserBuilder().setSigningKey(key).build();
        this.tokenValidityInMilliseconds = 1000 * tokenValidityInMilliseconds;
        this.tokenValidityInMillisecondsForRememberMe = 1000 * tokenValidityInMillisecondsForRememberMe;
        this.metersService = metersService;
    }

    public String createToken(Authentication authentication, boolean rememberMe) {

        String authorities = authentication.getAuthorities().stream()
                .map(grantedAuthority -> {
                    String authority = grantedAuthority.getAuthority();
                    return authority.startsWith("ROLE_") ? authority : "ROLE_" + authority;
                })
                .collect(Collectors.joining(","));


        long now = (new Date().getTime());
        Date validity;

        if (rememberMe) {
            validity = new Date(now + this.tokenValidityInMillisecondsForRememberMe);

        } else {

            validity = new Date(now + this.tokenValidityInMilliseconds);

        }

        return Jwts.builder().setSubject(authentication.getName()).claim(AUTHORITIES_KEY, authorities)
                .signWith(key, SignatureAlgorithm.HS512).setExpiration(validity).compact();
    }

    public Authentication getAuthentication(String token) {

        Claims claims = jwtParser.parseClaimsJws(token).getBody();

        Collection<? extends GrantedAuthority> authorities = Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                .filter(auth -> !auth.trim().isEmpty()).map(SimpleGrantedAuthority::new).collect(Collectors.toList());

        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);

    }

    public boolean validateToken(String authToken) {

        try {

            jwtParser.parseClaimsJws(authToken);
            return true;

        } catch (ExpiredJwtException exception) {

            this.metersService.trackTokenExpired();
            log.trace(INVALID_JWT_TOKEN, exception);

        } catch (UnsupportedJwtException exception) {

            this.metersService.trackTokenUnsupported();
            log.trace(INVALID_JWT_TOKEN, exception);

        } catch (MalformedJwtException exception) {

            this.metersService.trackTokenMalformed();
            log.trace(INVALID_JWT_TOKEN, exception);

        } catch (SignatureException exception) {

            this.metersService.trackTokenInvalidSignature();
            log.trace(INVALID_JWT_TOKEN, exception);

        } catch (IllegalArgumentException exception) {

            log.error("Token validation error {}", exception.getMessage());
        }

        return false;
    }

}