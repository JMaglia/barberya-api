package com.barberya.gasto;

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
import com.barberya.gasto.dto.ActualizarProveedorRequest;
import com.barberya.gasto.dto.CrearProveedorRequest;
import com.barberya.gasto.dto.ProveedorResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Proveedores de la barbería del token. Los permisos por rol están en SecurityConfig. */
@Tag(name = "Proveedores", description = "ABM de los proveedores de la barbería (baja lógica)")
@RestController
@RequestMapping("/api/v1/proveedores")
public class ProveedorController {

	private final ProveedorService proveedorService;

	public ProveedorController(ProveedorService proveedorService) {
		this.proveedorService = proveedorService;
	}

	/** Lista chica: sin paginar. Por defecto solo los activos (los que se ofrecen al cargar un gasto). */
	@GetMapping
	public List<ProveedorResponse> listar(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "false") boolean incluirInactivos) {
		return proveedorService.listar(UsuarioActual.barberiaId(jwt), incluirInactivos);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProveedorResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearProveedorRequest request) {
		return proveedorService.crear(UsuarioActual.barberiaId(jwt), request);
	}

	@PatchMapping("/{id}")
	public ProveedorResponse actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody ActualizarProveedorRequest request) {
		return proveedorService.actualizar(UsuarioActual.barberiaId(jwt), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void darDeBaja(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		proveedorService.darDeBaja(UsuarioActual.barberiaId(jwt), id);
	}

	@PostMapping("/{id}/reactivar")
	public ProveedorResponse reactivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return proveedorService.reactivar(UsuarioActual.barberiaId(jwt), id);
	}

}
