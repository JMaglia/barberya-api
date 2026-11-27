package com.barberya.gasto.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Gasto variable. No hay esFijo ni gastoFijoId: si vienen en el JSON se ignoran. */
public record CrearGastoRequest(
		@NotBlank(message = "Ingresá qué se compró")
		@Size(max = 200, message = "La descripción puede tener hasta 200 caracteres")
		String descripcion,

		@NotNull(message = "Ingresá el monto")
		@DecimalMin(value = "0", inclusive = false, message = "El monto tiene que ser mayor a 0")
		@Digits(integer = 10, fraction = 2, message = "El monto puede tener hasta 2 decimales")
		BigDecimal monto,

		@NotNull(message = "Ingresá la fecha")
		LocalDate fecha,

		@NotNull(message = "Elegí una categoría")
		Long categoriaId,

		Long proveedorId,

		@Positive(message = "La cantidad tiene que ser mayor a 0")
		Integer cantidad) {
}
