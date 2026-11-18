package com.barberya.disponibilidad.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Un día del rango. {@code horarios} vacío = no hay lugar (el front lo muestra grisado). */
public record DiaDisponibleResponse(LocalDate fecha, List<OffsetDateTime> horarios) {
}
