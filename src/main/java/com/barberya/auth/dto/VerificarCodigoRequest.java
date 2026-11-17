package com.barberya.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerificarCodigoRequest(
		@NotBlank(message = "Ingresá tu mail")
		String email,

		@NotBlank(message = "Ingresá el código")
		@Pattern(regexp = "\\d{6}", message = "El código tiene 6 números")
		String codigo,

		boolean mantenerSesion) {

	@Override
	public String toString() {
		return "VerificarCodigoRequest[mantenerSesion=" + mantenerSesion + "]";
	}

}
