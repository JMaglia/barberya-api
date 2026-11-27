package com.barberya.turno.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Turno que carga el barbero en su agenda. Va exactamente uno de {@code clienteId} (ficha existente) o
 * {@code clienteNuevo}.
 */
public record CargarTurnoRequest(
		@NotNull(message = "Elegí un servicio")
		Long servicioId,

		@NotNull(message = "Elegí un horario")
		OffsetDateTime inicio,

		Long clienteId,

		@Valid
		ClienteNuevoRequest clienteNuevo,

		@Size(max = 500, message = "Las notas pueden tener hasta 500 caracteres")
		String notas) {

	/** Queda en {@code errores.cliente}. */
	@JsonIgnore
	@Schema(hidden = true)
	@AssertTrue(message = "Elegí un cliente existente o cargá uno nuevo, no los dos")
	public boolean isCliente() {
		return (clienteId == null) != (clienteNuevo == null);
	}

	@Override
	public String toString() {
		return "CargarTurnoRequest[servicioId=" + servicioId + ", inicio=" + inicio + ", clienteId=" + clienteId + "]";
	}

}
