package com.barberya.servicio.dto;

import java.math.BigDecimal;

import com.barberya.servicio.Servicio;

/** Servicio visto desde el link público. El id hace falta para reservar. */
public record ServicioPublicoResponse(Long id, String nombre, String descripcion, int duracionMin, BigDecimal precio) {

	public static ServicioPublicoResponse de(Servicio s) {
		return new ServicioPublicoResponse(s.getId(), s.getNombre(), s.getDescripcion(), s.getDuracionMin(),
				s.getPrecio());
	}

}
