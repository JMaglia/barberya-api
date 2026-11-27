package com.barberya.gasto.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearProveedorRequest(
		@NotBlank(message = "Ingresá el nombre del proveedor")
		@Size(max = 120, message = "El nombre puede tener hasta 120 caracteres")
		String nombre,

		@Size(max = 80, message = "El rubro puede tener hasta 80 caracteres")
		String rubro,

		@Size(max = 30, message = "El teléfono puede tener hasta 30 caracteres")
		String telefono,

		@Email(message = "El mail no es válido")
		@Size(max = 255, message = "El mail puede tener hasta 255 caracteres")
		String email,

		@Size(max = 1000, message = "Las notas pueden tener hasta 1000 caracteres")
		String notas) {
}
