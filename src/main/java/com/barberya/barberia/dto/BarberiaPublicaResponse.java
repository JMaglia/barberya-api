package com.barberya.barberia.dto;

import com.barberya.barberia.Barberia;

/** Lo que ve cualquiera desde el link de la barbería. Nada interno (ni id, ni estado, ni pausa). */
public record BarberiaPublicaResponse(
		String nombre,
		String slug,
		String direccion,
		String telefono,
		String zonaHoraria,
		int cancelarHastaHoras,
		int reservaMaxDias) {

	public static BarberiaPublicaResponse de(Barberia b) {
		return new BarberiaPublicaResponse(b.getNombre(), b.getSlug(), b.getDireccion(), b.getTelefono(),
				b.getZonaHoraria(), b.getCancelarHastaHoras(), b.getReservaMaxDias());
	}

}
