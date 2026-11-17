package com.barberya.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitarCodigoRequest(
		@NotBlank(message = "Ingresá tu mail")
		@Email(message = "El mail no es válido")
		@Size(max = 255, message = "El mail puede tener hasta 255 caracteres")
		String email) {

	@Override
	public String toString() {
		return "SolicitarCodigoRequest[]";
	}

}
