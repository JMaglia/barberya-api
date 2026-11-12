package com.barberya.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecuperarRequest(
		@NotBlank(message = "Ingresá tu mail")
		@Email(message = "El mail no es válido")
		String email) {

	@Override
	public String toString() {
		return "RecuperarRequest[]";
	}

}
