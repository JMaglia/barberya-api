package com.barberya.turno;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.UsuarioActual;
import com.barberya.barberia.MiembroActual;
import com.barberya.turno.dto.TurnoClienteResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Turnos de la barbería del token; el rol DUENO lo exige SecurityConfig. */
@Tag(name = "Turnos", description = "Reservar y cancelar turnos")
@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

	private final TurnoService turnoService;
	private final MiembroActual miembroActual;

	public TurnoController(TurnoService turnoService, MiembroActual miembroActual) {
		this.turnoService = turnoService;
		this.miembroActual = miembroActual;
	}

	/** Sin límite de horas, pero solo turnos que todavía no empezaron. */
	@PostMapping("/{id}/cancelar")
	public TurnoClienteResponse cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		miembroActual.obtener(jwt);
		return turnoService.cancelarComoBarbero(UsuarioActual.barberiaId(jwt), id);
	}

}
