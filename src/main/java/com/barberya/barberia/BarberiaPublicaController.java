package com.barberya.barberia;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.agenda.HorarioService;
import com.barberya.agenda.dto.FranjaHorariaResponse;
import com.barberya.barberia.dto.BarberiaPublicaResponse;
import com.barberya.servicio.ServicioService;
import com.barberya.servicio.dto.ServicioPublicoResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Sin login: lo que ve el cliente desde el link de la barbería. Barbería inexistente o suspendida → 404. */
@Tag(name = "Público", description = "Datos de una barbería por slug, sin login")
@RestController
@RequestMapping("/api/v1/public/barberias/{slug}")
public class BarberiaPublicaController {

	private final BarberiaService barberiaService;
	private final ServicioService servicioService;
	private final HorarioService horarioService;

	public BarberiaPublicaController(BarberiaService barberiaService, ServicioService servicioService,
			HorarioService horarioService) {
		this.barberiaService = barberiaService;
		this.servicioService = servicioService;
		this.horarioService = horarioService;
	}

	@GetMapping
	public BarberiaPublicaResponse obtener(@PathVariable String slug) {
		return barberiaService.obtenerPublica(slug);
	}

	@GetMapping("/servicios")
	public List<ServicioPublicoResponse> servicios(@PathVariable String slug) {
		return servicioService.listarPublicos(slug);
	}

	/** Horario semanal para la sección "Horarios". Sin bloqueos ni ids de miembros. */
	@GetMapping("/horarios")
	public List<FranjaHorariaResponse> horarios(@PathVariable String slug) {
		return horarioService.listarPublico(slug);
	}

}
