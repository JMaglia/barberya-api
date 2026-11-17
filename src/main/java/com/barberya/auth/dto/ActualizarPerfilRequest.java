package com.barberya.auth.dto;

import com.barberya.validacion.Telefono;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Completa el perfil: el front pide nombre y celular antes de dejar reservar. */
public record ActualizarPerfilRequest(
		@NotBlank(message = "Ingresá tu nombre")
		@Size(max = 80, message = "El nombre puede tener hasta 80 caracteres")
		String nombre,

		@Size(max = 80, message = "El apellido puede tener hasta 80 caracteres")
		String apellido,

		@NotBlank(message = "Ingresá tu celular")
		@Pattern(regexp = Telefono.REGEX, message = Telefono.MENSAJE)
		String telefono) {

	@Override
	public String toString() {
		return "ActualizarPerfilRequest[]";
	}

}
