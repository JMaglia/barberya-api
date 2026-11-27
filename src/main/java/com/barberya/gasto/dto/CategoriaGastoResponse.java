package com.barberya.gasto.dto;

import com.barberya.gasto.CategoriaGasto;

public record CategoriaGastoResponse(Long id, String nombre) {

	public static CategoriaGastoResponse de(CategoriaGasto c) {
		return new CategoriaGastoResponse(c.getId(), c.getNombre());
	}

}
