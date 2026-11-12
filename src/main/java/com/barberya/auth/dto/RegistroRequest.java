package com.barberya.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
		@NotBlank(message = "Ingresá tu nombre")
		@Size(max = 80, message = "El nombre puede tener hasta 80 caracteres")
		String nombre,

		@NotBlank(message = "Ingresá tu mail")
		@Email(message = "El mail no es válido")
		@Size(max = 255, message = "El mail puede tener hasta 255 caracteres")
		String email,

		@NotBlank(message = "Ingresá tu celular")
		@Size(max = 30, message = "El celular puede tener hasta 30 caracteres")
		String telefono,

		// BCrypt ignora lo que pasa de 72 bytes.
		@NotBlank(message = "Ingresá una contraseña")
		@Size(min = 8, max = 72, message = "La contraseña tiene que tener entre 8 y 72 caracteres")
		String password) {

	@Override
	public String toString() {
		return "RegistroRequest[nombre=" + nombre + "]";
	}

}
