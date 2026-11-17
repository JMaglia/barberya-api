package com.barberya.auth;

/** Lo que sirve del ID token de Google. {@code nombre} y {@code apellido} pueden ser null. */
public record DatosGoogle(String sub, String email, String nombre, String apellido) {

	@Override
	public String toString() {
		return "DatosGoogle[]";
	}

}
