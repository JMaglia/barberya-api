package com.barberya.disponibilidad.dto;

import java.util.List;

/** Inicios libres para un servicio. Sin ids de miembros ni datos de turnos o bloqueos. */
public record DisponibilidadResponse(String zonaHoraria, int duracionMin, List<DiaDisponibleResponse> dias) {
}
