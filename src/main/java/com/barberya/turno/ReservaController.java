package com.barberya.turno;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.UsuarioActual;
import com.barberya.comun.dto.PaginaResponse;
import com.barberya.error.DatosInvalidosException;
import com.barberya.turno.dto.MiTurnoResponse;
import com.barberya.turno.dto.ReservarTurnoRequest;
import com.barberya.turno.dto.TurnoClienteResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Lo que hace el cliente con sus turnos. Requiere login, pero no rol: el usuario sale del token. */
@Tag(name = "Turnos", description = "Reservar y cancelar turnos")
@RestController
@RequestMapping("/api/v1")
public class ReservaController {

	private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

	private final TurnoService turnoService;
	private final MisTurnosService misTurnosService;

	public ReservaController(TurnoService turnoService, MisTurnosService misTurnosService) {
		this.turnoService = turnoService;
		this.misTurnosService = misTurnosService;
	}

	@PostMapping("/barberias/{slug}/turnos")
	@ResponseStatus(HttpStatus.CREATED)
	public TurnoClienteResponse reservar(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug,
			@Valid @RequestBody ReservarTurnoRequest request) {
		return turnoService.reservar(UsuarioActual.usuarioId(jwt), slug, request);
	}

	/**
	 * Mis turnos en todas las barberías. {@code alcance=proximos} (default): CONFIRMADO desde ahora, ascendente.
	 * {@code alcance=pasados}: el resto, descendente.
	 */
	@GetMapping("/mis-turnos")
	public PaginaResponse<MiTurnoResponse> misTurnos(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "proximos") String alcance,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		AlcanceMisTurnos valor = AlcanceMisTurnos.desde(alcance)
				.orElseThrow(() -> new DatosInvalidosException("alcance", "El parámetro alcance no es válido"));
		return misTurnosService.listar(UsuarioActual.usuarioId(jwt), valor, page, size);
	}

	/** Archivo iCalendar para agregar el turno al calendario del cliente. */
	@GetMapping("/mis-turnos/{id}/calendario.ics")
	public ResponseEntity<String> calendario(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		String ics = misTurnosService.calendario(UsuarioActual.usuarioId(jwt), id);
		return ResponseEntity.ok()
				.contentType(TEXT_CALENDAR)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("turno-" + id + ".ics").build().toString())
				.body(ics);
	}

	@PostMapping("/mis-turnos/{id}/cancelar")
	public TurnoClienteResponse cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return turnoService.cancelarComoCliente(UsuarioActual.usuarioId(jwt), id);
	}

}
