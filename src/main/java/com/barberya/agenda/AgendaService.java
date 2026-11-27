package com.barberya.agenda;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.agenda.dto.AgendaResponse;
import com.barberya.agenda.dto.AgendaResponse.BloqueoDia;
import com.barberya.agenda.dto.AgendaResponse.DiaAgenda;
import com.barberya.barberia.Barberia;
import com.barberya.barberia.BarberiaRepository;
import com.barberya.error.DatosInvalidosException;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.turno.Turno;
import com.barberya.turno.TurnoRepository;
import com.barberya.turno.dto.TurnoAgendaResponse;

/** La agenda del miembro por días locales de la barbería. Dos consultas: turnos (con servicio y cliente) y bloqueos. */
@Service
public class AgendaService {

	/** Días que puede abarcar una consulta, contando desde y hasta. */
	static final int DIAS_MAXIMOS = 31;

	private final BarberiaRepository barberias;
	private final TurnoRepository turnos;
	private final BloqueoAgendaRepository bloqueos;

	public AgendaService(BarberiaRepository barberias, TurnoRepository turnos, BloqueoAgendaRepository bloqueos) {
		this.barberias = barberias;
		this.turnos = turnos;
		this.bloqueos = bloqueos;
	}

	@Transactional(readOnly = true)
	public AgendaResponse ver(Long barberiaId, Long miembroId, LocalDate desde, LocalDate hasta,
			boolean incluirCancelados) {
		if (hasta.isBefore(desde)) {
			throw new DatosInvalidosException("hasta", "La fecha \"hasta\" no puede ser anterior a \"desde\"");
		}
		if (ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS) {
			throw new DatosInvalidosException("hasta", "El rango puede ser de hasta " + DIAS_MAXIMOS + " días");
		}
		Barberia barberia = barberias.findById(barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos tu barbería"));
		ZoneId zona = ZoneId.of(barberia.getZonaHoraria());
		Instant inicioRango = desde.atStartOfDay(zona).toInstant();
		Instant finRango = hasta.plusDays(1).atStartOfDay(zona).toInstant();

		List<Turno> turnosRango = turnos.buscarAgenda(barberiaId, miembroId, inicioRango, finRango, incluirCancelados);
		List<BloqueoAgenda> bloqueosRango = bloqueos.buscarEnRango(miembroId, inicioRango, finRango);

		List<DiaAgenda> dias = new ArrayList<>();
		for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
			LocalDate dia = fecha;
			Instant inicioDia = dia.atStartOfDay(zona).toInstant();
			Instant finDia = dia.plusDays(1).atStartOfDay(zona).toInstant();
			List<TurnoAgendaResponse> turnosDia = turnosRango.stream()
					.filter(t -> t.getInicio().atZone(zona).toLocalDate().equals(dia))
					.map(t -> TurnoAgendaResponse.de(t, zona))
					.toList();
			List<BloqueoDia> bloqueosDia = bloqueosRango.stream()
					.filter(b -> b.getInicio().isBefore(finDia) && b.getFin().isAfter(inicioDia))
					.map(b -> BloqueoDia.de(b, zona))
					.toList();
			dias.add(new DiaAgenda(dia, turnosDia, bloqueosDia));
		}
		return new AgendaResponse(zona.getId(), dias);
	}

}
