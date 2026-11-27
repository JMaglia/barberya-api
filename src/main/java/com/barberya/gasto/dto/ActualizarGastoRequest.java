package com.barberya.gasto.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Edición parcial: un campo en null no cambia. Para sacar el proveedor o la cantidad se usan
 * {@code quitarProveedor} / {@code quitarCantidad} (no se pueden mandar junto con el valor).
 */
public record ActualizarGastoRequest(
		@Pattern(regexp = "(?s).*\\S.*", message = "Ingresá qué se compró")
		@Size(max = 200, message = "La descripción puede tener hasta 200 caracteres")
		String descripcion,

		@DecimalMin(value = "0", inclusive = false, message = "El monto tiene que ser mayor a 0")
		@Digits(integer = 10, fraction = 2, message = "El monto puede tener hasta 2 decimales")
		BigDecimal monto,

		LocalDate fecha,

		Long categoriaId,

		Long proveedorId,

		@Positive(message = "La cantidad tiene que ser mayor a 0")
		Integer cantidad,

		Boolean quitarProveedor,

		Boolean quitarCantidad) {
}
