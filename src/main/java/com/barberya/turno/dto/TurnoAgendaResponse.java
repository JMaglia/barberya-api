package com.barberya.turno.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.barberya.cliente.Cliente;
import com.barberya.servicio.Servicio;
import com.barberya.turno.CanceladoPor;
import com.barberya.turno.EstadoTurno;
import com.barberya.turno.Turno;
import com.barberya.turno.dto.TurnoClienteResponse.ServicioTurno;

/**
 * El turno visto por el barbero en su agenda. Fechas con el offset de la barbería. Con el teléfono del cliente el front
 * arma el botón opcional de WhatsApp. {@code canceladoPor} es null salvo en los cancelados.
 */
public record TurnoAgendaResponse(
		Long id,
		OffsetDateTime inicio,
		OffsetDateTime fin,
		EstadoTurno estado,
		BigDecimal precio,
		ServicioTurno servicio,
		ClienteTurno cliente,
		CanceladoPor canceladoPor) {

	public record ClienteTurno(String nombre, String telefono) {
	}

	public static TurnoAgendaResponse de(Turno t, ZoneId zona) {
		Servicio s = t.getServicio();
		Cliente c = t.getCliente();
		return new TurnoAgendaResponse(t.getId(), t.getInicio().atZone(zona).toOffsetDateTime(),
				t.getFin().atZone(zona).toOffsetDateTime(), t.getEstado(), t.getPrecio(),
				new ServicioTurno(s.getNombre(), s.getDuracionMin()), new ClienteTurno(c.getNombre(), c.getTelefono()),
				t.getCanceladoPor());
	}

}
