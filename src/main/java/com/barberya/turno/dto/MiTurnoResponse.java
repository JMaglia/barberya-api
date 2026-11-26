package com.barberya.turno.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

import com.barberya.turno.EstadoTurno;
import com.barberya.turno.Turno;
import com.barberya.turno.dto.TurnoClienteResponse.BarberiaTurno;
import com.barberya.turno.dto.TurnoClienteResponse.ServicioTurno;

/**
 * Un turno en "Mis turnos": lo mismo que {@link TurnoClienteResponse}, más {@code servicioId} (para "Repetir turno")
 * y {@code puedeCancelar} (CONFIRMADO y antes de {@code cancelableHasta}). Sin ids de miembro ni de ficha.
 */
public record MiTurnoResponse(
		Long id,
		OffsetDateTime inicio,
		OffsetDateTime fin,
		EstadoTurno estado,
		BigDecimal precio,
		ServicioTurno servicio,
		BarberiaTurno barberia,
		OffsetDateTime cancelableHasta,
		Long servicioId,
		boolean puedeCancelar) {

	public static MiTurnoResponse de(Turno t, Instant ahora) {
		TurnoClienteResponse base = TurnoClienteResponse.de(t);
		boolean puedeCancelar = t.getEstado() == EstadoTurno.CONFIRMADO && ahora.isBefore(t.cancelableHasta());
		return new MiTurnoResponse(base.id(), base.inicio(), base.fin(), base.estado(), base.precio(), base.servicio(),
				base.barberia(), base.cancelableHasta(), t.getServicio().getId(), puedeCancelar);
	}

}
