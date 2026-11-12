package com.barberya.auth.dto;

import com.barberya.auth.Usuario;

public record UsuarioResponse(Long id, String nombre, String email, String telefono) {

	public static UsuarioResponse de(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getTelefono());
	}

}
