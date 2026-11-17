package com.barberya.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
		@NotBlank(message = "Falta el token de Google")
		String idToken,

		boolean mantenerSesion) {

	@Override
	public String toString() {
		return "GoogleLoginRequest[mantenerSesion=" + mantenerSesion + "]";
	}

}
