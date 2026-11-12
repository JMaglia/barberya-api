package com.barberya.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerRequest(
		@NotBlank(message = "Falta el token")
		String token,

		@NotBlank(message = "Ingresá una contraseña")
		@Size(min = 8, max = 72, message = "La contraseña tiene que tener entre 8 y 72 caracteres")
		String nuevaPassword) {

	@Override
	public String toString() {
		return "RestablecerRequest[]";
	}

}
