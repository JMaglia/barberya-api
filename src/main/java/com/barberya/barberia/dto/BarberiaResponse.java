package com.barberya.barberia.dto;

import com.barberya.barberia.Barberia;

/** La barbería vista por su dueño. */
public record BarberiaResponse(
		Long id,
		String nombre,
		String slug,
		String telefono,
		String direccion,
		String zonaHoraria,
		int cancelarHastaHoras,
		int reservaMaxDias,
		int pausaEntreTurnosMin,
		boolean activa) {

	public static BarberiaResponse de(Barberia b) {
		return new BarberiaResponse(b.getId(), b.getNombre(), b.getSlug(), b.getTelefono(), b.getDireccion(),
				b.getZonaHoraria(), b.getCancelarHastaHoras(), b.getReservaMaxDias(), b.getPausaEntreTurnosMin(),
				b.isActiva());
	}

}
