package com.barberya.auth.dto;

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
		// Acepta el vacío para que ese caso lo reporte solo @NotBlank, con su mensaje.
		@Pattern(regexp = "^$|^\\+?[0-9 ()-]{8,29}$", message = "El celular no es válido")
		String telefono) {

	@Override
	public String toString() {
		return "ActualizarPerfilRequest[]";
	}

}
