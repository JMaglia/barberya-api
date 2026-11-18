package com.barberya.barberia;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.agenda.HorarioService;
import com.barberya.agenda.dto.FranjaHorariaResponse;
import com.barberya.barberia.dto.BarberiaPublicaResponse;
import com.barberya.disponibilidad.DisponibilidadService;
import com.barberya.disponibilidad.dto.DisponibilidadResponse;
import com.barberya.servicio.ServicioService;
import com.barberya.servicio.dto.ServicioPublicoResponse;

/** Sin login: lo que ve el cliente desde el link de la barbería. Barbería inexistente o suspendida → 404. */
@RestController
@RequestMapping("/api/v1/public/barberias/{slug}")
public class BarberiaPublicaController {

	private final BarberiaService barberiaService;
	private final ServicioService servicioService;
	private final HorarioService horarioService;
	private final DisponibilidadService disponibilidadService;

	public BarberiaPublicaController(BarberiaService barberiaService, ServicioService servicioService,
			HorarioService horarioService, DisponibilidadService disponibilidadService) {
		this.barberiaService = barberiaService;
		this.servicioService = servicioService;
		this.horarioService = horarioService;
		this.disponibilidadService = disponibilidadService;
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

	/**
	 * Inicios libres para un servicio, día por día. {@code desde} y {@code hasta} son fechas locales de la barbería
	 * (hasta 14 días); si {@code desde} ya pasó, arranca hoy.
	 */
	@GetMapping("/disponibilidad")
	public DisponibilidadResponse disponibilidad(@PathVariable String slug, @RequestParam Long servicioId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return disponibilidadService.listar(slug, servicioId, desde, hasta);
	}

}
