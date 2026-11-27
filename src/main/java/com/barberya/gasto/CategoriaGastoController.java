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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.UsuarioActual;
import com.barberya.gasto.dto.CategoriaGastoRequest;
import com.barberya.gasto.dto.CategoriaGastoResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Categorías de gasto de la barbería del token. Los permisos por rol están en SecurityConfig. */
@Tag(name = "Categorías de gasto", description = "ABM de las categorías de gasto de la barbería")
@RestController
@RequestMapping("/api/v1/categorias-gasto")
public class CategoriaGastoController {

	private final CategoriaGastoService categoriaService;

	public CategoriaGastoController(CategoriaGastoService categoriaService) {
		this.categoriaService = categoriaService;
	}

	/** Lista chica: sin paginar, por nombre. */
	@GetMapping
	public List<CategoriaGastoResponse> listar(@AuthenticationPrincipal Jwt jwt) {
		return categoriaService.listar(UsuarioActual.barberiaId(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoriaGastoResponse crear(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CategoriaGastoRequest request) {
		return categoriaService.crear(UsuarioActual.barberiaId(jwt), request);
	}

	@PatchMapping("/{id}")
	public CategoriaGastoResponse renombrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody CategoriaGastoRequest request) {
		return categoriaService.renombrar(UsuarioActual.barberiaId(jwt), id, request);
	}

	/** Borrado definitivo; si la categoría tiene gastos, 409. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		categoriaService.borrar(UsuarioActual.barberiaId(jwt), id);
	}

}
