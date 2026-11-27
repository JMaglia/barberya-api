package com.barberya.turno.dto;

import java.util.List;

import com.barberya.turno.AdvertenciaTurno;

/** El turno recién cargado y los avisos que no impidieron guardarlo (lista vacía si no hay). */
public record TurnoCargadoResponse(TurnoAgendaResponse turno, List<AdvertenciaTurno> advertencias) {
}
