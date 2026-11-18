package com.barberya.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.dto.ActualizarPerfilRequest;
import com.barberya.auth.dto.MeResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Me", description = "Perfil del usuario logueado")
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

	/** Completa el perfil (nombre y celular). El usuario sale del token. */
	@PatchMapping
	public MeResponse actualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ActualizarPerfilRequest request) {
		return authService.actualizarPerfil(Long.valueOf(jwt.getSubject()), request);
	}

}
