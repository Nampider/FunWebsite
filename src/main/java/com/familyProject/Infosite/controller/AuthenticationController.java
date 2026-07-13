package com.familyProject.Infosite.controller;

import com.familyProject.Infosite.dto.AuthResponse;
import com.familyProject.Infosite.dto.CsrfResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    /*
     * Public endpoint.
     *
     * The frontend calls this before login and again
     * after a successful login or logout.
     */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    /*
     * Requires an authenticated CHATTER user.
     */
    @GetMapping("/me")
    public AuthResponse currentUser(Authentication authentication) {

        return new AuthResponse(true, authentication.getName());
    }
}
