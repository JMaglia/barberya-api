package com.barberya.servicio.dto;

import java.math.BigDecimal;

import com.barberya.validacion.MultiploDe;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edición parcial: un campo en null no cambia. {@code descripcion} vacía la borra. */
public record ActualizarServicioRequest(
		@Pattern(regexp = ".*\\S.*", message = "Ingresá el nombre del servicio")
		@Size(max = 80, message = "El nombre puede tener hasta 80 caracteres")
		String nombre,

		@Size(max = 500, message = "La descripción puede tener hasta 500 caracteres")
		String descripcion,

		@Min(value = 5, message = "La duración va de 5 a 480 minutos")
		@Max(value = 480, message = "La duración va de 5 a 480 minutos")
		@MultiploDe(value = 5, message = "La duración tiene que ser múltiplo de 5")
		Integer duracionMin,

		@DecimalMin(value = "0", inclusive = false, message = "El precio tiene que ser mayor a 0")
		@Digits(integer = 10, fraction = 2, message = "El precio puede tener hasta 2 decimales")
		BigDecimal precio) {
}
