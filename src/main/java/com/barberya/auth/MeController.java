package com.barberya.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.dto.MeResponse;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

	private final AuthService authService;

	public MeController(AuthService authService) {
		this.authService = authService;
	}

	/** Datos frescos de la base, no del token. */
	@GetMapping
	public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return authService.me(Long.valueOf(jwt.getSubject()));
	}

}
