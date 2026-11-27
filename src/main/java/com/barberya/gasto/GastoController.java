package com.barberya.gasto;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
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
import com.barberya.gasto.dto.ActualizarGastoRequest;
import com.barberya.gasto.dto.CrearGastoRequest;
import com.barberya.gasto.dto.GastoResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Gastos variables de la barbería del token. Los gastos fijos no se manejan acá. */
@Tag(name = "Gastos", description = "Carga y consulta de los gastos variables")
@RestController
@RequestMapping("/api/v1/gastos")
public class GastoController {

	private final GastoService gastoService;

	public GastoController(GastoService gastoService) {
		this.gastoService = gastoService;
	}

	/** De {@code desde} a {@code hasta} inclusive (hasta 366 días), más recientes primero. Sin paginar. */
	@GetMapping
	public List<GastoResponse> listar(@AuthenticationPrincipal Jwt jwt,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate hasta) {
		return gastoService.listar(UsuarioActual.barberiaId(jwt), desde, hasta);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GastoResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CrearGastoRequest request) {
		return gastoService.crear(UsuarioActual.barberiaId(jwt), request);
	}

	@PatchMapping("/{id}")
	public GastoResponse actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody ActualizarGastoRequest request) {
		return gastoService.actualizar(UsuarioActual.barberiaId(jwt), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void borrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		gastoService.borrar(UsuarioActual.barberiaId(jwt), id);
	}

}
