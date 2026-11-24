package com.barberya.turno;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.UsuarioActual;
import com.barberya.turno.dto.ReservarTurnoRequest;
import com.barberya.turno.dto.TurnoClienteResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Lo que hace el cliente con sus turnos. Requiere login, pero no rol: el usuario sale del token. */
@Tag(name = "Turnos", description = "Reservar y cancelar turnos")
@RestController
@RequestMapping("/api/v1")
public class ReservaController {

	private final TurnoService turnoService;

	public ReservaController(TurnoService turnoService) {
		this.turnoService = turnoService;
	}

	@PostMapping("/barberias/{slug}/turnos")
	@ResponseStatus(HttpStatus.CREATED)
	public TurnoClienteResponse reservar(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug,
			@Valid @RequestBody ReservarTurnoRequest request) {
		return turnoService.reservar(UsuarioActual.usuarioId(jwt), slug, request);
	}

	@PostMapping("/mis-turnos/{id}/cancelar")
	public TurnoClienteResponse cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return turnoService.cancelarComoCliente(UsuarioActual.usuarioId(jwt), id);
	}

}
