package com.barberya.turno;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.comun.dto.PaginaResponse;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.turno.dto.MiTurnoResponse;

/** "Mis turnos" del cliente: los de todas sus fichas, en todas las barberías. El usuario sale siempre del token. */
@Service
@Transactional(readOnly = true)
public class MisTurnosService {

	private final TurnoRepository turnos;
	private final Clock reloj;

	public MisTurnosService(TurnoRepository turnos, Clock reloj) {
		this.turnos = turnos;
		this.reloj = reloj;
	}

	public PaginaResponse<MiTurnoResponse> listar(Long usuarioId, AlcanceMisTurnos alcance, int page, int size) {
		Instant ahora = reloj.instant();
		PageRequest pagina = PageRequest.of(page, size);
		Page<Turno> resultado = switch (alcance) {
			case PROXIMOS -> turnos.findProximosDeUsuario(usuarioId, ahora, pagina);
			case PASADOS -> turnos.findPasadosDeUsuario(usuarioId, ahora, pagina);
		};
		return PaginaResponse.de(resultado.map(t -> MiTurnoResponse.de(t, ahora)));
	}

	/** El .ics de un turno propio. Un turno ajeno da 404. */
	public String calendario(Long usuarioId, Long turnoId) {
		Turno turno = turnos.findDeUsuarioConDetalle(turnoId, usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese turno"));
		return CalendarioIcs.generar(turno, reloj.instant());
	}

}
