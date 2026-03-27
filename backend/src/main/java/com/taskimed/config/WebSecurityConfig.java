package com.taskimed.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    private static final String[] PUBLIC_URLS = {
        "/api/users/login",
        "/api/users/register",
        "/api/users/invite/**",
        "/api/users/validate-invite",
        "/api/users/register-invite",
        "/api/users/validate-token",
        "/profile.jsf",
        "/profile.xhtml",
        "/backend/profile.jsf",
        "/backend/profile.xhtml",
        "/javax.faces.resource/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/",
        "/index.xhtml"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // Filtro condicional que aplica o ignora el JwtRequestFilter según el tipo de recurso
        OncePerRequestFilter conditionalJwtFilter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {

                String path = request.getRequestURI();

                // Ignorar recursos JSF y XHTML
                if (path.endsWith(".jsf") || path.endsWith(".xhtml")) {
                    chain.doFilter(request, response);
                } else {
                    jwtRequestFilter.doFilter(request, response, chain);
                }
            }
        };

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_URLS).permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login.xhtml?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        // Registrar el filtro condicional antes del UsernamePasswordAuthenticationFilter
        http.addFilterBefore(conditionalJwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
