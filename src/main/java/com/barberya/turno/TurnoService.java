package com.barberya.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.auth.Usuario;
import com.barberya.auth.UsuarioRepository;
import com.barberya.barberia.Barberia;
import com.barberya.barberia.BarberiaService;
import com.barberya.barberia.MiembroBarberia;
import com.barberya.barberia.MiembroBarberiaRepository;
import com.barberya.cliente.Cliente;
import com.barberya.cliente.ClienteRepository;
import com.barberya.disponibilidad.DisponibilidadService;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.error.Restricciones;
import com.barberya.servicio.Servicio;
import com.barberya.servicio.ServicioRepository;
import com.barberya.turno.dto.ReservarTurnoRequest;
import com.barberya.turno.dto.TurnoClienteResponse;

/** Reservar y cancelar turnos. "Ahora" sale siempre del {@link Clock}. */
@Service
public class TurnoService {

	/** Turnos futuros CONFIRMADO que puede tener un cliente en una misma barbería. */
	static final int TOPE_TURNOS_FUTUROS = 4;

	private static final String RESTRICCION_SUPERPOSICION = "turnos_sin_superposicion";
	private static final String RESTRICCION_FICHA_UNICA = "clientes_barberia_id_usuario_id_key";
	private static final int LARGO_NOMBRE_FICHA = 80;

	private final BarberiaService barberiaService;
	private final ServicioRepository servicios;
	private final UsuarioRepository usuarios;
	private final MiembroBarberiaRepository miembros;
	private final ClienteRepository clientes;
	private final TurnoRepository turnos;
	private final DisponibilidadService disponibilidadService;
	private final Clock reloj;

	public TurnoService(BarberiaService barberiaService, ServicioRepository servicios, UsuarioRepository usuarios,
			MiembroBarberiaRepository miembros, ClienteRepository clientes, TurnoRepository turnos,
			DisponibilidadService disponibilidadService, Clock reloj) {
		this.barberiaService = barberiaService;
		this.servicios = servicios;
		this.usuarios = usuarios;
		this.miembros = miembros;
		this.clientes = clientes;
		this.turnos = turnos;
		this.disponibilidadService = disponibilidadService;
		this.reloj = reloj;
	}

	/**
	 * El cliente reserva desde el link de la barbería: acá la barbería sale del slug, porque el cliente no es miembro.
	 * La fila del usuario queda bloqueada hasta el commit, así sus reservas simultáneas no crean dos fichas ni se
	 * saltean el tope.
	 */
	@Transactional
	public TurnoClienteResponse reservar(Long usuarioId, String slug, ReservarTurnoRequest request) {
		Barberia barberia = barberiaService.buscarPublica(slug);
		Servicio servicio = servicios.findByIdAndBarberiaId(request.servicioId(), barberia.getId())
				.filter(Servicio::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese servicio"));
		Usuario usuario = usuarios.findByIdParaActualizar(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos tu cuenta"));
		if (!tieneTexto(usuario.getNombre()) || !tieneTexto(usuario.getTelefono())) {
			throw new ConflictoTurnoException(CodigoConflictoTurno.PERFIL_INCOMPLETO,
					"Completá tu nombre y celular para reservar");
		}
		if (miembros.existsByUsuarioIdAndBarberiaIdAndActivoTrue(usuarioId, barberia.getId())) {
			throw new ReservaEnBarberiaPropiaException();
		}

		Instant ahora = reloj.instant();
		Cliente ficha = fichaDe(barberia, usuario);
		if (turnos.countByClienteIdAndEstadoAndInicioGreaterThan(ficha.getId(), EstadoTurno.CONFIRMADO,
				ahora) >= TOPE_TURNOS_FUTUROS) {
			throw new ConflictoTurnoException(CodigoConflictoTurno.TOPE_TURNOS,
					"Ya tenés " + TOPE_TURNOS_FUTUROS + " turnos reservados en esta barbería");
		}

		Instant inicio = request.inicio().toInstant();
		MiembroBarberia miembro = disponibilidadService.estaDisponible(barberia, servicio, inicio)
				.orElseThrow(ConflictoTurnoException::horarioNoDisponible);
		Turno turno = new Turno(barberia, miembro, ficha, servicio, inicio,
				inicio.plus(Duration.ofMinutes(servicio.getDuracionMin())), servicio.getPrecio());
		try {
			turno = turnos.saveAndFlush(turno);
		}
		catch (DataIntegrityViolationException e) {
			if (Restricciones.es(e, RESTRICCION_SUPERPOSICION)) {
				throw ConflictoTurnoException.horarioNoDisponible();
			}
			throw e;
		}
		return TurnoClienteResponse.de(turno);
	}

	/** El cliente cancela un turno suyo, dentro del plazo de la barbería. Un turno ajeno da 404. */
	@Transactional
	public TurnoClienteResponse cancelarComoCliente(Long usuarioId, Long turnoId) {
		Turno turno = turnos.findParaCancelarDeUsuario(turnoId, usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese turno"));
		if (turno.getEstado() != EstadoTurno.CONFIRMADO) {
			throw ConflictoTurnoException.noCancelable();
		}
		Instant ahora = reloj.instant();
		int horas = turno.getBarberia().getCancelarHastaHoras();
		if (!ahora.isBefore(turno.getInicio().minus(Duration.ofHours(horas)))) {
			throw new ConflictoTurnoException(CodigoConflictoTurno.FUERA_DE_PLAZO,
					"Solo se puede cancelar hasta " + horas + (horas == 1 ? " hora" : " horas") + " antes");
		}
		return cancelar(turno, ahora, CanceladoPor.CLIENTE);
	}

	/** El barbero cancela un turno de su barbería que todavía no empezó, sin límite de horas. */
	@Transactional
	public TurnoClienteResponse cancelarComoBarbero(Long barberiaId, Long turnoId) {
		Turno turno = turnos.findParaCancelarDeBarberia(turnoId, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese turno"));
		if (turno.getEstado() != EstadoTurno.CONFIRMADO) {
			throw ConflictoTurnoException.noCancelable();
		}
		Instant ahora = reloj.instant();
		if (!turno.getInicio().isAfter(ahora)) {
			throw new ConflictoTurnoException(CodigoConflictoTurno.NO_CANCELABLE,
					"Este turno ya pasó: marcalo como completado o ausente desde la agenda");
		}
		return cancelar(turno, ahora, CanceladoPor.BARBERO);
	}

	private TurnoClienteResponse cancelar(Turno turno, Instant ahora, CanceladoPor quien) {
		turno.setEstado(EstadoTurno.CANCELADO);
		turno.setCanceladoEn(ahora);
		turno.setCanceladoPor(quien);
		return TurnoClienteResponse.de(turnos.saveAndFlush(turno));
	}

	/**
	 * Busca (bloqueada) o crea la ficha del usuario en la barbería. Una ficha existente solo se reactiva y completa los
	 * datos de contacto vacíos: no se pisa lo que haya cargado el barbero.
	 */
	private Cliente fichaDe(Barberia barberia, Usuario usuario) {
		Cliente ficha = clientes.findParaReservar(barberia.getId(), usuario.getId()).orElse(null);
		if (ficha != null) {
			ficha.setActivo(true);
			if (!tieneTexto(ficha.getTelefono())) {
				ficha.setTelefono(usuario.getTelefono());
			}
			if (!tieneTexto(ficha.getEmail())) {
				ficha.setEmail(usuario.getEmail());
			}
			return ficha;
		}
		ficha = new Cliente(barberia, nombreCompleto(usuario));
		ficha.setUsuario(usuario);
		ficha.setTelefono(usuario.getTelefono());
		ficha.setEmail(usuario.getEmail());
		try {
			return clientes.saveAndFlush(ficha);
		}
		catch (DataIntegrityViolationException e) {
			// La transacción ya quedó abortada en Postgres: no se puede reintentar la búsqueda acá adentro.
			if (Restricciones.es(e, RESTRICCION_FICHA_UNICA)) {
				throw new FichaEnCursoException();
			}
			throw e;
		}
	}

	private static String nombreCompleto(Usuario usuario) {
		String nombre = usuario.getNombre().trim();
		if (tieneTexto(usuario.getApellido())) {
			nombre = nombre + " " + usuario.getApellido().trim();
		}
		return nombre.length() > LARGO_NOMBRE_FICHA ? nombre.substring(0, LARGO_NOMBRE_FICHA).trim() : nombre;
	}

	private static boolean tieneTexto(String valor) {
		return valor != null && !valor.isBlank();
	}

}
