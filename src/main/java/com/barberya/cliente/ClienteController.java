package com.barberya.cliente;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.UsuarioActual;
import com.barberya.barberia.MiembroActual;
import com.barberya.cliente.dto.ClienteBusquedaResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Fichas de clientes de la barbería del token; el rol DUENO lo exige SecurityConfig. */
@Tag(name = "Clientes", description = "Fichas de clientes de la barbería")
@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

	private final ClienteService clienteService;
	private final MiembroActual miembroActual;

	public ClienteController(ClienteService clienteService, MiembroActual miembroActual) {
		this.clienteService = clienteService;
		this.miembroActual = miembroActual;
	}

	/** Hasta 10 fichas activas por nombre o teléfono, ordenadas por nombre. {@code q}: al menos 2 caracteres. */
	@GetMapping("/buscar")
	public List<ClienteBusquedaResponse> buscar(@AuthenticationPrincipal Jwt jwt, @RequestParam String q) {
		miembroActual.obtener(jwt);
		return clienteService.buscar(UsuarioActual.barberiaId(jwt), q);
	}

}
