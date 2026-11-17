package com.barberya.barberia.dto;

import com.barberya.barberia.SlugGenerador;
import com.barberya.validacion.Telefono;
import com.barberya.validacion.ZonaHorariaValida;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edición parcial: un campo en null no cambia. {@code direccion} vacía la borra.
 * No tiene id: la barbería sale siempre del token.
 */
public record ActualizarBarberiaRequest(
		@Pattern(regexp = ".*\\S.*", message = "Ingresá el nombre de la barbería")
		@Size(max = 120, message = "El nombre puede tener hasta 120 caracteres")
		String nombre,

		@Pattern(regexp = ".*\\S.*", message = "Ingresá el teléfono de la barbería")
		@Pattern(regexp = Telefono.REGEX, message = Telefono.MENSAJE)
		String telefono,

		@Size(max = 200, message = "La dirección puede tener hasta 200 caracteres")
		String direccion,

		@Size(min = SlugGenerador.LARGO_MIN, max = SlugGenerador.LARGO_MAX, message = "El link tiene que tener entre 3 y 50 caracteres")
		@Pattern(regexp = SlugGenerador.FORMATO, message = "El link solo puede tener minúsculas, números y guiones")
		String slug,

		@ZonaHorariaValida
		String zonaHoraria,

		@Min(value = 0, message = "Las horas para cancelar van de 0 a 168")
		@Max(value = 168, message = "Las horas para cancelar van de 0 a 168")
		Integer cancelarHastaHoras,

		@Min(value = 1, message = "Los días de anticipación van de 1 a 365")
		@Max(value = 365, message = "Los días de anticipación van de 1 a 365")
		Integer reservaMaxDias,

		@Min(value = 0, message = "La pausa entre turnos va de 0 a 120 minutos")
		@Max(value = 120, message = "La pausa entre turnos va de 0 a 120 minutos")
		Integer pausaEntreTurnosMin) {

	@Override
	public String toString() {
		return "ActualizarBarberiaRequest[nombre=" + nombre + ", slug=" + slug + "]";
	}

}
