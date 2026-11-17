package com.barberya.servicio.dto;

import java.math.BigDecimal;

import com.barberya.servicio.Servicio;

public record ServicioResponse(Long id, String nombre, String descripcion, int duracionMin, BigDecimal precio,
		boolean activo) {

	public static ServicioResponse de(Servicio s) {
		return new ServicioResponse(s.getId(), s.getNombre(), s.getDescripcion(), s.getDuracionMin(), s.getPrecio(),
				s.isActivo());
	}

}
