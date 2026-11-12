package com.barberya.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "Ingresá tu mail")
		String email,

		@NotBlank(message = "Ingresá tu contraseña")
		String password,

		boolean mantenerSesion) {

	@Override
	public String toString() {
		return "LoginRequest[mantenerSesion=" + mantenerSesion + "]";
	}

}
