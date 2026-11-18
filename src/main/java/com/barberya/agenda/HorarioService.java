package com.barberya.agenda;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.agenda.dto.FranjaHorariaRequest;
import com.barberya.agenda.dto.FranjaHorariaResponse;
import com.barberya.barberia.BarberiaService;
import com.barberya.barberia.MiembroBarberiaRepository;

/** El miembro llega ya resuelto desde el token (MiembroActual): nunca un id que mande el front. */
@Service
public class HorarioService {

	private final HorarioAtencionRepository horarios;
	private final MiembroBarberiaRepository miembros;
	private final BarberiaService barberiaService;

	public HorarioService(HorarioAtencionRepository horarios, MiembroBarberiaRepository miembros,
			BarberiaService barberiaService) {
		this.horarios = horarios;
		this.miembros = miembros;
		this.barberiaService = barberiaService;
	}

	@Transactional(readOnly = true)
	public List<FranjaHorariaResponse> listar(Long miembroId) {
		return horarios.findByMiembroIdOrderByDiaSemanaAscHoraInicioAsc(miembroId).stream()
				.map(FranjaHorariaResponse::de)
				.toList();
	}

	/** Reemplaza la semana entera. Lista vacía = cerrado todos los días. */
	@Transactional
	public List<FranjaHorariaResponse> reemplazar(Long miembroId, List<FranjaHorariaRequest> franjas) {
		ValidadorHorarios.validar(franjas);
		horarios.borrarDeMiembro(miembroId);
		var miembro = miembros.getReferenceById(miembroId);
		horarios.saveAll(franjas.stream()
				.map(f -> new HorarioAtencion(miembro, f.diaSemana().shortValue(), f.horaInicio(), f.horaFin()))
				.toList());
		return listar(miembroId);
	}

	@Transactional(readOnly = true)
	public List<FranjaHorariaResponse> listarPublico(String slug) {
		Long barberiaId = barberiaService.buscarPublica(slug).getId();
		return horarios.buscarPublicosDeBarberia(barberiaId).stream()
				.map(FranjaHorariaResponse::de)
				.distinct()
				.toList();
	}

}
