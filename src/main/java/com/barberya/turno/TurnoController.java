package com.barberya.turno;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.UsuarioActual;
import com.barberya.barberia.MiembroActual;
import com.barberya.turno.dto.TurnoAgendaResponse;
import com.barberya.turno.dto.TurnoClienteResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Turnos de la barbería del token; el rol DUENO lo exige SecurityConfig. */
@Tag(name = "Turnos", description = "Cancelar turnos y marcarlos como completados o ausentes")
@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

	private final TurnoService turnoService;
	private final EstadoTurnoService estadoTurnoService;
	private final MiembroActual miembroActual;

	public TurnoController(TurnoService turnoService, EstadoTurnoService estadoTurnoService,
			MiembroActual miembroActual) {
		this.turnoService = turnoService;
		this.estadoTurnoService = estadoTurnoService;
		this.miembroActual = miembroActual;
	}

	/** Sin límite de horas, pero solo turnos que todavía no empezaron. */
	@PostMapping("/{id}/cancelar")
	public TurnoClienteResponse cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		miembroActual.obtener(jwt);
		return turnoService.cancelarComoBarbero(UsuarioActual.barberiaId(jwt), id);
	}

	/** Un turno confirmado que ya empezó pasa a completado y se crea su cobro. */
	@PostMapping("/{id}/completar")
	public TurnoAgendaResponse completar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		miembroActual.obtener(jwt);
		return estadoTurnoService.completar(UsuarioActual.barberiaId(jwt), id);
	}

	/** Un turno confirmado que ya empezó pasa a ausente: el horario queda libre. */
	@PostMapping("/{id}/ausente")
	public TurnoAgendaResponse ausente(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		miembroActual.obtener(jwt);
		return estadoTurnoService.marcarAusente(UsuarioActual.barberiaId(jwt), id);
	}

	/** Un turno completado o ausente vuelve a confirmado; si estaba completado se borra su cobro. */
	@PostMapping("/{id}/revertir")
	public TurnoAgendaResponse revertir(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		miembroActual.obtener(jwt);
		return estadoTurnoService.revertir(UsuarioActual.barberiaId(jwt), id);
	}

}
