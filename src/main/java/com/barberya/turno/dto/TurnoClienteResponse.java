package com.barberya.turno.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.barberya.barberia.Barberia;
import com.barberya.servicio.Servicio;
import com.barberya.turno.EstadoTurno;
import com.barberya.turno.Turno;

/**
 * El turno visto por el cliente. Fechas con el offset de la barbería. Sin ids de miembro ni de cliente.
 * {@code cancelableHasta}: el cliente puede cancelar antes de ese instante.
 */
public record TurnoClienteResponse(
		Long id,
		OffsetDateTime inicio,
		OffsetDateTime fin,
		EstadoTurno estado,
		BigDecimal precio,
		ServicioTurno servicio,
		BarberiaTurno barberia,
		OffsetDateTime cancelableHasta) {

	public record ServicioTurno(String nombre, int duracionMin) {
	}

	public record BarberiaTurno(String nombre, String slug, String direccion, String telefono) {
	}

	public static TurnoClienteResponse de(Turno t) {
		Barberia b = t.getBarberia();
		Servicio s = t.getServicio();
		ZoneId zona = ZoneId.of(b.getZonaHoraria());
		return new TurnoClienteResponse(t.getId(), t.getInicio().atZone(zona).toOffsetDateTime(),
				t.getFin().atZone(zona).toOffsetDateTime(), t.getEstado(), t.getPrecio(),
				new ServicioTurno(s.getNombre(), s.getDuracionMin()),
				new BarberiaTurno(b.getNombre(), b.getSlug(), b.getDireccion(), b.getTelefono()),
				t.getInicio().minus(Duration.ofHours(b.getCancelarHastaHoras())).atZone(zona).toOffsetDateTime());
	}

}
