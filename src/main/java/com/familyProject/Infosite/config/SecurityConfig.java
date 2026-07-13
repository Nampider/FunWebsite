package com.familyProject.Infosite.config;

import com.familyProject.Infosite.dto.ApiError;
import com.familyProject.Infosite.dto.AuthResponse;
import com.familyProject.Infosite.dto.ChatProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    UserDetailsService userDetailsService(
            ChatProperties properties,
            PasswordEncoder passwordEncoder) {

        InMemoryUserDetailsManager manager =
                new InMemoryUserDetailsManager();
        Set<String> usernames = new HashSet<>();

        for (ChatProperties.AllowedUser allowedUser : properties.users()) {
            String username = allowedUser.username()
                    .toLowerCase(Locale.ROOT);

            if (!usernames.add(username)) {
                throw new IllegalStateException(
                        "Duplicate configured username: " + username
                );
            }

            manager.createUser(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(
                            allowedUser.password()
                    ))
                    .roles("CHATTER")
                    .build());
        }

        return manager;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            ChatProperties properties) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "OPTIONS")
        );
        configuration.setAllowedHeaders(List.of(
                "Content-Type",
                "Accept",
                "X-CSRF-TOKEN",
                "X-XSRF-TOKEN"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        /* Fail at startup instead of accepting an unsafe wildcard+cookie setup. */
        configuration.validateAllowCredentials();

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonMapper jsonMapper) throws Exception {

        http
                .cors(Customizer.withDefaults())

                /*
                 * The token is stored in the HTTP session, masked in the JSON
                 * response, and resolved from the header on mutating requests.
                 */
                .csrf(Customizer.withDefaults())

                .requestCache(cache -> cache.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                        .sessionFixation(fixation ->
                                fixation.changeSessionId()
                        )
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, cause) ->
                                writeJson(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        new ApiError(
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication is required.",
                                                Instant.now()
                                        ),
                                        jsonMapper
                                )
                        )
                        .accessDeniedHandler((request, response, cause) ->
                                writeJson(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        new ApiError(
                                                "ACCESS_DENIED",
                                                "The request was denied.",
                                                Instant.now()
                                        ),
                                        jsonMapper
                                )
                        )
                )

                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/csrf",
                                "/actuator/health",
                                "/actuator/health/**"
                        )
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        )
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .anyRequest()
                        .hasRole("CHATTER")
                )

                .formLogin(form -> form
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) ->
                                writeJson(
                                        response,
                                        HttpStatus.OK,
                                        new AuthResponse(
                                                true,
                                                authentication.getName()
                                        ),
                                        jsonMapper
                                )
                        )
                        .failureHandler((request, response, cause) ->
                                writeJson(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        new ApiError(
                                                "INVALID_CREDENTIALS",
                                                "Invalid username or password.",
                                                Instant.now()
                                        ),
                                        jsonMapper
                                )
                        )
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeJson(
                                        response,
                                        HttpStatus.OK,
                                        new AuthResponse(false, null),
                                        jsonMapper
                                )
                        )
                );

        return http.build();
    }

    private static void writeJson(
            HttpServletResponse response,
            HttpStatus status,
            Object body,
            JsonMapper jsonMapper) throws IOException {

        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
