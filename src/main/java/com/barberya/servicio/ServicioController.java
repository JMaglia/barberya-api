package com.barberya.servicio;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.UsuarioActual;
import com.barberya.servicio.dto.ActualizarServicioRequest;
import com.barberya.servicio.dto.CrearServicioRequest;
import com.barberya.servicio.dto.ServicioResponse;

import jakarta.validation.Valid;

/** Servicios de la barbería del token. Los permisos por rol están en SecurityConfig. */
@RestController
@RequestMapping("/api/v1/servicios")
public class ServicioController {

	private final ServicioService servicioService;

	public ServicioController(ServicioService servicioService) {
		this.servicioService = servicioService;
	}

	/** Catálogo chico: sin paginar. Activos primero. */
	@GetMapping
	public List<ServicioResponse> listar(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "false") boolean incluirInactivos) {
		return servicioService.listar(UsuarioActual.barberiaId(jwt), incluirInactivos);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ServicioResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearServicioRequest request) {
		return servicioService.crear(UsuarioActual.barberiaId(jwt), request);
	}

	@PatchMapping("/{id}")
	public ServicioResponse actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody ActualizarServicioRequest request) {
		return servicioService.actualizar(UsuarioActual.barberiaId(jwt), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void darDeBaja(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		servicioService.darDeBaja(UsuarioActual.barberiaId(jwt), id);
	}

	@PostMapping("/{id}/reactivar")
	public ServicioResponse reactivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return servicioService.reactivar(UsuarioActual.barberiaId(jwt), id);
	}

}
