package com.barberya.barberia.dto;

import com.barberya.barberia.SlugGenerador;
import com.barberya.validacion.Telefono;
import com.barberya.validacion.ZonaHorariaValida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code slug} y {@code zonaHoraria} son opcionales: si no vienen, se genera el slug y se usa la de Buenos Aires. */
public record CrearBarberiaRequest(
		@NotBlank(message = "Ingresá el nombre de la barbería")
		@Size(max = 120, message = "El nombre puede tener hasta 120 caracteres")
		String nombre,

		@NotBlank(message = "Ingresá el teléfono de la barbería")
		@Pattern(regexp = Telefono.REGEX, message = Telefono.MENSAJE)
		String telefono,

		@Size(max = 200, message = "La dirección puede tener hasta 200 caracteres")
		String direccion,

		@Size(min = SlugGenerador.LARGO_MIN, max = SlugGenerador.LARGO_MAX, message = "El link tiene que tener entre 3 y 50 caracteres")
		@Pattern(regexp = SlugGenerador.FORMATO, message = "El link solo puede tener minúsculas, números y guiones")
		String slug,

		@ZonaHorariaValida
		String zonaHoraria) {

	@Override
	public String toString() {
		return "CrearBarberiaRequest[nombre=" + nombre + ", slug=" + slug + "]";
	}

}
