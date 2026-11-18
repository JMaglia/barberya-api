package com.barberya.barberia;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.UsuarioActual;
import com.barberya.barberia.dto.ActualizarBarberiaRequest;
import com.barberya.barberia.dto.BarberiaCreadaResponse;
import com.barberya.barberia.dto.BarberiaResponse;
import com.barberya.barberia.dto.CrearBarberiaRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** {@code /barberia} (singular) es siempre la del token; los permisos por rol están en SecurityConfig. */
@Tag(name = "Barbería", description = "Alta y datos de la barbería del token")
@RestController
@RequestMapping("/api/v1")
public class BarberiaController {

	private final BarberiaService barberiaService;

	public BarberiaController(BarberiaService barberiaService) {
		this.barberiaService = barberiaService;
	}

	@PostMapping("/barberias")
	@ResponseStatus(HttpStatus.CREATED)
	public BarberiaCreadaResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearBarberiaRequest request) {
		return barberiaService.crear(UsuarioActual.usuarioId(jwt), request);
	}

	@GetMapping("/barberia")
	public BarberiaResponse obtener(@AuthenticationPrincipal Jwt jwt) {
		return barberiaService.obtener(UsuarioActual.barberiaId(jwt));
	}

	@PatchMapping("/barberia")
	public BarberiaResponse actualizar(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ActualizarBarberiaRequest request) {
		return barberiaService.actualizar(UsuarioActual.barberiaId(jwt), request);
	}

}
