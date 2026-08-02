package com.saryom.rideservice.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <token>} by
 * delegating to a {@link TokenVerifier}. On success the Firebase uid becomes the
 * authentication principal (authority {@code ROLE_USER}); a present-but-invalid
 * token is rejected with 401 problem+json. Requests without a bearer token pass
 * through unauthenticated so Spring Security decides per route.
 */
@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final TokenVerifier tokenVerifier;

    public FirebaseAuthenticationFilter(TokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring("Bearer ".length()).trim();
        try {
            VerifiedUser user = tokenVerifier.verify(token);
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.uid(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            authentication.setDetails(user);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidTokenException e) {
            SecurityContextHolder.clearContext();
            ProblemJson.write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage());
        }
    }
}
