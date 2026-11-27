package com.barberya.turno.dto;

import com.barberya.validacion.Telefono;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Ficha que crea el barbero al cargar un turno: queda sin usuario. */
public record ClienteNuevoRequest(
		@NotBlank(message = "Ingresá el nombre del cliente")
		@Size(max = 80, message = "El nombre puede tener hasta 80 caracteres")
		String nombre,

		@Pattern(regexp = Telefono.REGEX, message = Telefono.MENSAJE)
		String telefono,

		@Email(message = "El mail no es válido")
		@Size(max = 255, message = "El mail puede tener hasta 255 caracteres")
		String email) {

	/** Sin datos personales, por si termina en un log. */
	@Override
	public String toString() {
		return "ClienteNuevoRequest[]";
	}

}
