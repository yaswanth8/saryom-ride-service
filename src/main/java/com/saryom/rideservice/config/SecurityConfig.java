package com.saryom.rideservice.config;

import com.saryom.rideservice.auth.FirebaseAuthenticationFilter;
import com.saryom.rideservice.auth.RestAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security. Browsing rides is public: {@code GET /api/rides}
 * and {@code GET /api/rides/{id}}. Everything else (posting, booking, managing)
 * requires authentication. Authorization is enforced in the service layer via
 * {@code CurrentUser.requireUid()} rather than by path matching (Spring Security
 * 7's PathPatternRequestMatcher matches bare collection paths inconsistently);
 * the filter still authenticates when a bearer token is present, and the API
 * gateway remains the primary auth boundary.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    FirebaseAuthenticationFilter firebaseFilter,
                                    RestAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Run the filter only inside the security chain, not as a servlet-registered filter. */
    @Bean
    FilterRegistrationBean<FirebaseAuthenticationFilter> disableFirebaseFilterAutoRegistration(
            FirebaseAuthenticationFilter filter) {
        FilterRegistrationBean<FirebaseAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
