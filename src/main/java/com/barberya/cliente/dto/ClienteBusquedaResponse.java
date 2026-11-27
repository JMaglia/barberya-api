package com.barberya.cliente.dto;

import com.barberya.cliente.Cliente;

/** Lo justo para elegir la ficha al cargar un turno. */
public record ClienteBusquedaResponse(Long id, String nombre, String telefono) {

	public static ClienteBusquedaResponse de(Cliente c) {
		return new ClienteBusquedaResponse(c.getId(), c.getNombre(), c.getTelefono());
	}

}
