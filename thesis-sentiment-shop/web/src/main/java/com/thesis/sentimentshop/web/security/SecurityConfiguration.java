package com.thesis.sentimentshop.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Minimal authentication stub.
 *
 * <p>Every request is auto-authenticated as the fixed test user
 * {@code testuser}. This is a deliberate simplification and a
 * realistic auth flow would only add measurement noise (login, CSRF,
 * password hashing) without informing the QAs under analysis. The stub
 * is structured so that it can be replaced by a real authentication
 * mechanism without touching any controller: controllers consume
 * {@code @AuthenticationPrincipal} as they would in production.
 *
 * <p>CSRF is disabled because there are no browser-form-driven mutations
 * authenticated by session cookies; the SPA frontend uses JSON requests
 * and does not require CSRF tokens in this stub configuration.
 */
@Configuration
public class SecurityConfiguration {

    public static final String STUB_USERNAME = "testuser";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new StubAuthenticationFilter(),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static class StubAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain)
                throws ServletException, IOException {
            UserDetails user = User.withUsername(STUB_USERNAME)
                    .password("")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        }
    }
}
