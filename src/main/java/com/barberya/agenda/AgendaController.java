package com.barberya.agenda;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.agenda.dto.AgendaResponse;
import com.barberya.auth.UsuarioActual;
import com.barberya.barberia.MiembroActual;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Agenda del miembro actual. El rol DUENO lo exige SecurityConfig. */
@Tag(name = "Agenda", description = "Turnos y bloqueos por día")
@RestController
@RequestMapping("/api/v1/agenda")
public class AgendaController {

	private final AgendaService agendaService;
	private final MiembroActual miembroActual;

	public AgendaController(AgendaService agendaService, MiembroActual miembroActual) {
		this.agendaService = agendaService;
		this.miembroActual = miembroActual;
	}

	/** Días locales de la barbería, de {@code desde} a {@code hasta} inclusive (hasta 31 días). */
	@GetMapping
	public AgendaResponse ver(@AuthenticationPrincipal Jwt jwt,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "false") boolean incluirCancelados) {
		Long miembroId = miembroActual.obtener(jwt).getId();
		return agendaService.ver(UsuarioActual.barberiaId(jwt), miembroId, desde, hasta, incluirCancelados);
	}

}
