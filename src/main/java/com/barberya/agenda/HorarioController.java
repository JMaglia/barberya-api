package com.barberya.agenda;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.agenda.dto.FranjaHorariaRequest;
import com.barberya.agenda.dto.FranjaHorariaResponse;
import com.barberya.barberia.MiembroActual;

import jakarta.validation.Valid;

/** Horario semanal del miembro actual. Los permisos por rol están en SecurityConfig. */
@RestController
@RequestMapping("/api/v1/horarios")
public class HorarioController {

	private final HorarioService horarioService;
	private final MiembroActual miembroActual;

	public HorarioController(HorarioService horarioService, MiembroActual miembroActual) {
		this.horarioService = horarioService;
		this.miembroActual = miembroActual;
	}

	/** Ordenado por día (0 = domingo) y hora de inicio. */
	@GetMapping
	public List<FranjaHorariaResponse> listar(@AuthenticationPrincipal Jwt jwt) {
		return horarioService.listar(miembroActual.obtener(jwt).getId());
	}

	@PutMapping
	public List<FranjaHorariaResponse> reemplazar(@AuthenticationPrincipal Jwt jwt,
			@RequestBody List<@Valid FranjaHorariaRequest> franjas) {
		return horarioService.reemplazar(miembroActual.obtener(jwt).getId(), franjas);
	}

}
