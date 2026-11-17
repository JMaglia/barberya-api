package com.barberya.barberia;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.barberia.dto.BarberiaPublicaResponse;
import com.barberya.servicio.ServicioService;
import com.barberya.servicio.dto.ServicioPublicoResponse;

/** Sin login: lo que ve el cliente desde el link de la barbería. Barbería inexistente o suspendida → 404. */
@RestController
@RequestMapping("/api/v1/public/barberias/{slug}")
public class BarberiaPublicaController {

	private final BarberiaService barberiaService;
	private final ServicioService servicioService;

	public BarberiaPublicaController(BarberiaService barberiaService, ServicioService servicioService) {
		this.barberiaService = barberiaService;
		this.servicioService = servicioService;
	}

	@GetMapping
	public BarberiaPublicaResponse obtener(@PathVariable String slug) {
		return barberiaService.obtenerPublica(slug);
	}

	@GetMapping("/servicios")
	public List<ServicioPublicoResponse> servicios(@PathVariable String slug) {
		return servicioService.listarPublicos(slug);
	}

}
