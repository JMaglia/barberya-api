package com.barberya.gasto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Para crear y para renombrar: la categoría solo tiene nombre. */
public record CategoriaGastoRequest(
		@NotBlank(message = "Ingresá el nombre de la categoría")
		@Size(max = 60, message = "El nombre puede tener hasta 60 caracteres")
		String nombre) {
}
