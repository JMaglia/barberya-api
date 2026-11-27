package com.barberya.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.barberia.Barberia;
import com.barberya.barberia.BarberiaRepository;
import com.barberya.barberia.MiembroBarberia;
import com.barberya.barberia.MiembroBarberiaRepository;
import com.barberya.cliente.Cliente;
import com.barberya.cliente.ClienteService;
import com.barberya.disponibilidad.DisponibilidadService;
import com.barberya.error.DatosInvalidosException;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.error.Restricciones;
import com.barberya.servicio.Servicio;
import com.barberya.servicio.ServicioRepository;
import com.barberya.turno.dto.CargarTurnoRequest;
import com.barberya.turno.dto.ClienteNuevoRequest;
import com.barberya.turno.dto.TurnoAgendaResponse;
import com.barberya.turno.dto.TurnoCargadoResponse;

/**
 * El barbero carga un turno en su agenda (alguien que le escribió o que vino sin turno). No le aplican las reglas del
 * cliente (horario, grilla, anticipación, reserva_max_dias, tope): solo no puede pisar otro turno. Lo que no impide
 * cargar vuelve como advertencia. "Ahora" sale siempre del {@link Clock}.
 */
@Service
public class TurnoManualService {

	/** Cuánto antes de ahora puede haber empezado: para registrar a quien vino sin turno y después completarlo. */
	static final Duration ATRASO_MAXIMO = Duration.ofHours(24);
	static final Duration ANTICIPACION_MAXIMA = Duration.ofDays(365);

	private static final String RESTRICCION_SUPERPOSICION = "turnos_sin_superposicion";
	/** Para {@link TurnoRepository#existeSuperpuesto}: un turno nuevo no tiene id que excluir. */
	private static final Long SIN_EXCLUIR = -1L;

	private final BarberiaRepository barberias;
	private final MiembroBarberiaRepository miembros;
	private final ServicioRepository servicios;
	private final TurnoRepository turnos;
	private final ClienteService clienteService;
	private final DisponibilidadService disponibilidadService;
	private final Clock reloj;

	public TurnoManualService(BarberiaRepository barberias, MiembroBarberiaRepository miembros,
			ServicioRepository servicios, TurnoRepository turnos, ClienteService clienteService,
			DisponibilidadService disponibilidadService, Clock reloj) {
		this.barberias = barberias;
		this.miembros = miembros;
		this.servicios = servicios;
		this.turnos = turnos;
		this.clienteService = clienteService;
		this.disponibilidadService = disponibilidadService;
		this.reloj = reloj;
	}

	/**
	 * Todo en una transacción: si el turno no se puede guardar, tampoco queda la ficha nueva. La superposición se valida
	 * antes de crear la ficha y, para las cargas simultáneas, la ataja la base.
	 */
	@Transactional
	public TurnoCargadoResponse cargar(Long barberiaId, Long miembroId, CargarTurnoRequest request) {
		Barberia barberia = barberias.findById(barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos tu barbería"));
		Servicio servicio = servicios.findByIdAndBarberiaId(request.servicioId(), barberiaId)
				.filter(Servicio::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese servicio"));

		Instant ahora = reloj.instant();
		Instant inicio = request.inicio().toInstant();
		if (inicio.isBefore(ahora.minus(ATRASO_MAXIMO))) {
			throw new DatosInvalidosException("inicio", "Podés cargar turnos que empezaron hasta 24 horas antes");
		}
		if (inicio.isAfter(ahora.plus(ANTICIPACION_MAXIMA))) {
			throw new DatosInvalidosException("inicio", "Podés cargar turnos hasta un año adelante");
		}
		Instant fin = inicio.plus(Duration.ofMinutes(servicio.getDuracionMin()));
		if (turnos.existeSuperpuesto(miembroId, inicio, fin, SIN_EXCLUIR)) {
			throw horarioOcupado();
		}

		Cliente ficha = fichaDe(barberia, request);
		MiembroBarberia miembro = miembros.getReferenceById(miembroId);
		Turno turno = new Turno(barberia, miembro, ficha, servicio, inicio, fin, servicio.getPrecio());
		turno.setNotas(textoOpcional(request.notas()));
		try {
			turno = turnos.saveAndFlush(turno);
		}
		catch (DataIntegrityViolationException e) {
			if (Restricciones.es(e, RESTRICCION_SUPERPOSICION)) {
				throw horarioOcupado();
			}
			throw e;
		}

		List<AdvertenciaTurno> advertencias = new ArrayList<>();
		if (!disponibilidadService.entraEnHorario(barberia, miembroId, inicio, fin)) {
			advertencias.add(AdvertenciaTurno.FUERA_DE_HORARIO);
		}
		if (disponibilidadService.pisaBloqueo(miembroId, inicio, fin)) {
			advertencias.add(AdvertenciaTurno.PISA_BLOQUEO);
		}
		return new TurnoCargadoResponse(TurnoAgendaResponse.de(turno, ZoneId.of(barberia.getZonaHoraria())),
				List.copyOf(advertencias));
	}

	private Cliente fichaDe(Barberia barberia, CargarTurnoRequest request) {
		if (request.clienteId() != null) {
			return clienteService.buscarYReactivar(barberia.getId(), request.clienteId());
		}
		ClienteNuevoRequest nuevo = request.clienteNuevo();
		return clienteService.crearManual(barberia, nuevo.nombre(), nuevo.telefono(), nuevo.email());
	}

	private static ConflictoTurnoException horarioOcupado() {
		return new ConflictoTurnoException(CodigoConflictoTurno.HORARIO_OCUPADO, "Ese horario ya lo ocupa otro turno");
	}

	private static String textoOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.strip();
	}

}
