package com.barberya.disponibilidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.agenda.HorarioAtencion;
import com.barberya.agenda.HorarioAtencionRepository;
import com.barberya.barberia.Barberia;
import com.barberya.barberia.BarberiaService;
import com.barberya.barberia.MiembroBarberia;
import com.barberya.disponibilidad.dto.DiaDisponibleResponse;
import com.barberya.disponibilidad.dto.DisponibilidadResponse;
import com.barberya.error.DatosInvalidosException;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.servicio.Servicio;
import com.barberya.servicio.ServicioRepository;
import com.barberya.turno.EstadoTurno;

/**
 * Carga lo que necesita {@link CalculadoraDisponibilidad} en pocas consultas (franjas, turnos y bloqueos de todo el
 * rango) y arma la respuesta. Si hubiera varios miembros, un inicio está libre si alguno lo tiene libre.
 */
@Service
public class DisponibilidadService {

	static final Duration PASO = Duration.ofMinutes(15);
	static final int RANGO_MAXIMO_DIAS = 14;
	/** Los mismos que cuenta turnos_sin_superposicion: CANCELADO y AUSENTE liberan el horario. */
	static final Set<EstadoTurno> ESTADOS_QUE_OCUPAN = Set.of(EstadoTurno.CONFIRMADO, EstadoTurno.COMPLETADO);

	private final BarberiaService barberiaService;
	private final ServicioRepository servicios;
	private final HorarioAtencionRepository horarios;
	private final DisponibilidadRepository ocupaciones;
	private final Clock reloj;

	public DisponibilidadService(BarberiaService barberiaService, ServicioRepository servicios,
			HorarioAtencionRepository horarios, DisponibilidadRepository ocupaciones, Clock reloj) {
		this.barberiaService = barberiaService;
		this.servicios = servicios;
		this.horarios = horarios;
		this.ocupaciones = ocupaciones;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public DisponibilidadResponse listar(String slug, Long servicioId, LocalDate desde, LocalDate hasta) {
		Barberia barberia = barberiaService.buscarPublica(slug);
		Servicio servicio = servicios.findByIdAndBarberiaId(servicioId, barberia.getId())
				.filter(Servicio::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese servicio"));
		if (hasta.isBefore(desde)) {
			throw new DatosInvalidosException("hasta", "La fecha \"hasta\" no puede ser anterior a \"desde\"");
		}
		if (ChronoUnit.DAYS.between(desde, hasta) >= RANGO_MAXIMO_DIAS) {
			throw new DatosInvalidosException("hasta", "El rango puede ser de hasta " + RANGO_MAXIMO_DIAS + " días");
		}

		ZoneId zona = zona(barberia);
		Instant ahora = reloj.instant();
		LocalDate hoy = LocalDate.ofInstant(ahora, zona);
		LocalDate desdeEfectivo = desde.isBefore(hoy) ? hoy : desde;

		List<DiaDisponibleResponse> dias = new ArrayList<>();
		if (!hasta.isBefore(desdeEfectivo)) {
			calcular(barberia, servicio, desdeEfectivo, hasta, ahora).forEach((fecha, inicios) -> dias.add(
					new DiaDisponibleResponse(fecha, inicios.keySet().stream()
							.map(t -> t.atZone(zona).toOffsetDateTime())
							.toList())));
		}
		return new DisponibilidadResponse(barberia.getZonaHoraria(), servicio.getDuracionMin(), dias);
	}

	/**
	 * Para reservar (paso 10): el miembro que tiene libre ese inicio para ese servicio, o vacío. Usa el mismo cálculo
	 * que el listado, así un inicio que se ofreció es exactamente un inicio que se puede reservar.
	 */
	@Transactional(readOnly = true)
	public Optional<MiembroBarberia> estaDisponible(Barberia barberia, Servicio servicio, Instant inicio) {
		LocalDate fecha = LocalDate.ofInstant(inicio, zona(barberia));
		return Optional.ofNullable(calcular(barberia, servicio, fecha, fecha, reloj.instant()).get(fecha).get(inicio));
	}

	/** Por día, cada inicio libre con el primer miembro (por id) que lo tiene libre. */
	private Map<LocalDate, NavigableMap<Instant, MiembroBarberia>> calcular(Barberia barberia, Servicio servicio,
			LocalDate desde, LocalDate hasta, Instant ahora) {
		ZoneId zona = zona(barberia);
		Duration pausa = Duration.ofMinutes(barberia.getPausaEntreTurnosMin());
		LocalDate fechaMaxima = LocalDate.ofInstant(ahora, zona).plusDays(barberia.getReservaMaxDias());
		Instant inicioRango = desde.atStartOfDay(zona).toInstant();
		Instant finRango = hasta.plusDays(1).atStartOfDay(zona).toInstant();

		Map<LocalDate, NavigableMap<Instant, MiembroBarberia>> resultado = new LinkedHashMap<>();
		for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
			resultado.put(fecha, new TreeMap<>());
		}

		// Ordenado por id del miembro: si varios tienen libre el mismo inicio, gana siempre el mismo.
		NavigableMap<Long, List<HorarioAtencion>> franjasPorMiembro = horarios.buscarPublicosDeBarberia(barberia.getId())
				.stream()
				.collect(Collectors.groupingBy(h -> h.getMiembro().getId(), TreeMap::new, Collectors.toList()));
		if (franjasPorMiembro.isEmpty()) {
			return resultado;
		}
		Set<Long> miembroIds = franjasPorMiembro.keySet();
		Map<Long, List<Intervalo>> turnosPorMiembro = agrupar(ocupaciones.turnosQueOcupan(barberia.getId(), miembroIds,
				ESTADOS_QUE_OCUPAN, inicioRango.minus(pausa), finRango.plus(pausa)));
		Map<Long, List<Intervalo>> bloqueosPorMiembro = agrupar(
				ocupaciones.bloqueosEnRango(miembroIds, inicioRango, finRango));

		for (var entrada : franjasPorMiembro.entrySet()) {
			Long miembroId = entrada.getKey();
			MiembroBarberia miembro = entrada.getValue().getFirst().getMiembro();
			List<FranjaSemanal> franjas = entrada.getValue().stream()
					.map(h -> new FranjaSemanal(h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin()))
					.toList();
			var parametros = new ParametrosDisponibilidad(zona, desde, hasta, franjas,
					turnosPorMiembro.getOrDefault(miembroId, List.of()),
					bloqueosPorMiembro.getOrDefault(miembroId, List.of()),
					Duration.ofMinutes(servicio.getDuracionMin()), pausa, PASO, ahora, fechaMaxima);
			CalculadoraDisponibilidad.calcular(parametros).forEach((fecha, inicios) -> {
				for (ZonedDateTime inicio : inicios) {
					resultado.get(fecha).putIfAbsent(inicio.toInstant(), miembro);
				}
			});
		}
		return resultado;
	}

	private static Map<Long, List<Intervalo>> agrupar(List<OcupacionMiembro> lista) {
		return lista.stream().collect(Collectors.groupingBy(OcupacionMiembro::miembroId,
				Collectors.mapping(OcupacionMiembro::intervalo, Collectors.toList())));
	}

	private static ZoneId zona(Barberia barberia) {
		return ZoneId.of(barberia.getZonaHoraria());
	}

}
