package com.barberya.agenda;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.agenda.dto.BloqueoCreadoResponse;
import com.barberya.agenda.dto.BloqueoResponse;
import com.barberya.agenda.dto.CrearBloqueoRequest;
import com.barberya.barberia.MiembroBarberiaRepository;
import com.barberya.error.DatosInvalidosException;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.turno.EstadoTurno;
import com.barberya.turno.TurnoRepository;

/** Se permiten bloqueos superpuestos entre sí. Crear uno no cancela turnos: solo cuenta los afectados. */
@Service
public class BloqueoService {

	static final Duration DURACION_MAXIMA = Duration.ofDays(90);
	static final Duration ANTICIPACION_MAXIMA = Duration.ofDays(365);
	static final Duration RANGO_MAXIMO_CONSULTA = Duration.ofDays(92);

	private final BloqueoAgendaRepository bloqueos;
	private final MiembroBarberiaRepository miembros;
	private final TurnoRepository turnos;

	public BloqueoService(BloqueoAgendaRepository bloqueos, MiembroBarberiaRepository miembros, TurnoRepository turnos) {
		this.bloqueos = bloqueos;
		this.miembros = miembros;
		this.turnos = turnos;
	}

	@Transactional
	public BloqueoCreadoResponse crear(Long miembroId, CrearBloqueoRequest request) {
		Instant inicio = request.inicio().toInstant();
		Instant fin = request.fin().toInstant();
		Instant ahora = Instant.now();
		if (!fin.isAfter(inicio)) {
			throw new DatosInvalidosException("fin", "El fin tiene que ser posterior al inicio");
		}
		if (!fin.isAfter(ahora)) {
			throw new DatosInvalidosException("fin", "El bloqueo ya terminó: el fin tiene que ser futuro");
		}
		if (inicio.isAfter(ahora.plus(ANTICIPACION_MAXIMA))) {
			throw new DatosInvalidosException("inicio", "El bloqueo puede empezar como mucho dentro de un año");
		}
		if (Duration.between(inicio, fin).compareTo(DURACION_MAXIMA) > 0) {
			throw new DatosInvalidosException("fin", "Un bloqueo puede durar hasta 90 días");
		}

		BloqueoAgenda bloqueo = new BloqueoAgenda(miembros.getReferenceById(miembroId), inicio, fin);
		bloqueo.setMotivo(textoOpcional(request.motivo()));
		bloqueo = bloqueos.save(bloqueo);
		long afectados = turnos.countByMiembroIdAndEstadoAndInicioLessThanAndFinGreaterThan(miembroId,
				EstadoTurno.CONFIRMADO, fin, inicio);
		return BloqueoCreadoResponse.de(bloqueo, afectados);
	}

	@Transactional(readOnly = true)
	public List<BloqueoResponse> listar(Long miembroId, OffsetDateTime desde, OffsetDateTime hasta) {
		Instant d = desde.toInstant();
		Instant h = hasta.toInstant();
		if (!h.isAfter(d)) {
			throw new DatosInvalidosException("hasta", "La fecha \"hasta\" tiene que ser posterior a \"desde\"");
		}
		if (Duration.between(d, h).compareTo(RANGO_MAXIMO_CONSULTA) > 0) {
			throw new DatosInvalidosException("hasta", "El rango puede ser de hasta 92 días");
		}
		return bloqueos.buscarEnRango(miembroId, d, h).stream().map(BloqueoResponse::de).toList();
	}

	/** Borrado físico. Uno de otra barbería da 404, para no revelar que existe. */
	@Transactional
	public void borrar(Long barberiaId, Long id) {
		BloqueoAgenda bloqueo = bloqueos.findByIdAndMiembroBarberiaId(id, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese bloqueo"));
		bloqueos.delete(bloqueo);
	}

	private static String textoOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

}
