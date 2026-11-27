package com.barberya.gasto.dto;

import com.barberya.gasto.Proveedor;

public record ProveedorResponse(Long id, String nombre, String rubro, String telefono, String email, String notas,
		boolean activo) {

	public static ProveedorResponse de(Proveedor p) {
		return new ProveedorResponse(p.getId(), p.getNombre(), p.getRubro(), p.getTelefono(), p.getEmail(), p.getNotas(),
				p.isActivo());
	}

}
