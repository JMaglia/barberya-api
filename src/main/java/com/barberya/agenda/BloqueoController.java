package com.barberya.agenda;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.agenda.dto.BloqueoCreadoResponse;
import com.barberya.agenda.dto.BloqueoResponse;
import com.barberya.agenda.dto.CrearBloqueoRequest;
import com.barberya.auth.UsuarioActual;
import com.barberya.barberia.MiembroActual;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Bloqueos de agenda del miembro actual. Los permisos por rol están en SecurityConfig. */
@Tag(name = "Bloqueos", description = "Bloqueos de agenda: vacaciones, francos")
@RestController
@RequestMapping("/api/v1/bloqueos")
public class BloqueoController {

	private final BloqueoService bloqueoService;
	private final MiembroActual miembroActual;

	public BloqueoController(BloqueoService bloqueoService, MiembroActual miembroActual) {
		this.bloqueoService = bloqueoService;
		this.miembroActual = miembroActual;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BloqueoCreadoResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearBloqueoRequest request) {
		return bloqueoService.crear(miembroActual.obtener(jwt).getId(), request);
	}

	/** Los que se superponen con [desde, hasta), ordenados por inicio. Rango máximo: 92 días. */
	@GetMapping
	public List<BloqueoResponse> listar(@AuthenticationPrincipal Jwt jwt,
			@RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime desde,
			@RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime hasta) {
		return bloqueoService.listar(miembroActual.obtener(jwt).getId(), desde, hasta);
	}

	/** Busca por la barbería del token, a través del miembro. Antes exige que el usuario siga siendo miembro activo. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		miembroActual.obtener(jwt);
		bloqueoService.borrar(UsuarioActual.barberiaId(jwt), id);
	}

}
