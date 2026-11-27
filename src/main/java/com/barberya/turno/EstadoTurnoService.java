package com.barberya.turno;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.cobro.Cobro;
import com.barberya.cobro.CobroRepository;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.error.Restricciones;
import com.barberya.turno.dto.TurnoAgendaResponse;

/**
 * El barbero marca sus turnos como completados o ausentes, o revierte esa marca. La fila del turno queda bloqueada
 * hasta el commit. "Ahora" sale siempre del {@link Clock}.
 */
@Service
public class EstadoTurnoService {

	private static final String RESTRICCION_SUPERPOSICION = "turnos_sin_superposicion";
	private static final String RESTRICCION_COBRO_UNICO = "cobros_turno_id_key";

	private final TurnoRepository turnos;
	private final CobroRepository cobros;
	private final Clock reloj;

	public EstadoTurnoService(TurnoRepository turnos, CobroRepository cobros, Clock reloj) {
		this.turnos = turnos;
		this.cobros = cobros;
		this.reloj = reloj;
	}

	/** CONFIRMADO y ya empezado → COMPLETADO, con su cobro automático (precio del turno, sin medio de pago). */
	@Transactional
	public TurnoAgendaResponse completar(Long barberiaId, Long turnoId) {
		Turno turno = buscar(barberiaId, turnoId);
		Instant ahora = reloj.instant();
		exigirConfirmadoYEmpezado(turno, ahora);
		if (cobros.existsByTurnoId(turno.getId())) {
			throw yaCobrado();
		}
		turno.setEstado(EstadoTurno.COMPLETADO);
		Cobro cobro = new Cobro(turno.getBarberia(), turno.getPrecio(), ahora);
		cobro.setTurno(turno);
		try {
			cobros.saveAndFlush(cobro);
		}
		catch (DataIntegrityViolationException e) {
			if (Restricciones.es(e, RESTRICCION_COBRO_UNICO)) {
				throw yaCobrado();
			}
			throw e;
		}
		return respuesta(turno);
	}

	/** CONFIRMADO y ya empezado → AUSENTE. Eso libera el horario. */
	@Transactional
	public TurnoAgendaResponse marcarAusente(Long barberiaId, Long turnoId) {
		Turno turno = buscar(barberiaId, turnoId);
		exigirConfirmadoYEmpezado(turno, reloj.instant());
		turno.setEstado(EstadoTurno.AUSENTE);
		turnos.saveAndFlush(turno);
		return respuesta(turno);
	}

	/**
	 * COMPLETADO → CONFIRMADO, borrando su cobro (como entidad, así el cambio del turno no se pierde); AUSENTE →
	 * CONFIRMADO si su horario sigue libre, porque vuelve a ocuparlo.
	 */
	@Transactional
	public TurnoAgendaResponse revertir(Long barberiaId, Long turnoId) {
		Turno turno = buscar(barberiaId, turnoId);
		switch (turno.getEstado()) {
			case COMPLETADO -> {
				cobros.findByTurnoIdAndBarberiaId(turno.getId(), barberiaId).ifPresent(cobros::delete);
				turno.setEstado(EstadoTurno.CONFIRMADO);
				turnos.saveAndFlush(turno);
			}
			case AUSENTE -> {
				if (turnos.existeSuperpuesto(turno.getMiembro().getId(), turno.getInicio(), turno.getFin(),
						turno.getId())) {
					throw horarioOcupado();
				}
				turno.setEstado(EstadoTurno.CONFIRMADO);
				try {
					turnos.saveAndFlush(turno);
				}
				catch (DataIntegrityViolationException e) {
					if (Restricciones.es(e, RESTRICCION_SUPERPOSICION)) {
						throw horarioOcupado();
					}
					throw e;
				}
			}
			default -> throw new ConflictoTurnoException(CodigoConflictoTurno.ESTADO_INVALIDO,
					"Solo se puede revertir un turno completado o ausente");
		}
		return respuesta(turno);
	}

	private Turno buscar(Long barberiaId, Long turnoId) {
		return turnos.findParaModificarDeBarberia(turnoId, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese turno"));
	}

	/** El estado va primero: completar dos veces da ESTADO_INVALIDO aunque el turno no haya empezado. */
	private static void exigirConfirmadoYEmpezado(Turno turno, Instant ahora) {
		if (turno.getEstado() != EstadoTurno.CONFIRMADO) {
			throw new ConflictoTurnoException(CodigoConflictoTurno.ESTADO_INVALIDO,
					"Solo se puede marcar un turno confirmado");
		}
		if (turno.getInicio().isAfter(ahora)) {
			throw new ConflictoTurnoException(CodigoConflictoTurno.TODAVIA_NO_EMPEZO, "Este turno todavía no empezó");
		}
	}

	private static TurnoAgendaResponse respuesta(Turno turno) {
		return TurnoAgendaResponse.de(turno, ZoneId.of(turno.getBarberia().getZonaHoraria()));
	}

	private static ConflictoTurnoException yaCobrado() {
		return new ConflictoTurnoException(CodigoConflictoTurno.ESTADO_INVALIDO, "Este turno ya tiene un cobro");
	}

	private static ConflictoTurnoException horarioOcupado() {
		return new ConflictoTurnoException(CodigoConflictoTurno.HORARIO_OCUPADO, "Ese horario ya lo ocupa otro turno");
	}

}
