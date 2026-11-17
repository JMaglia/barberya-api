package com.barberya.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.barberia.BarberiaRepository;
import com.barberya.barberia.BarberiaService;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.servicio.dto.ActualizarServicioRequest;
import com.barberya.servicio.dto.CrearServicioRequest;
import com.barberya.servicio.dto.ServicioPublicoResponse;
import com.barberya.servicio.dto.ServicioResponse;

/** Todo filtra por la barbería del token; un servicio de otra barbería da 404, para no revelar que existe. */
@Service
public class ServicioService {

	private static final Sort POR_NOMBRE = Sort.by(Sort.Order.asc("nombre").ignoreCase());
	private static final Sort ACTIVOS_PRIMERO = Sort.by(Sort.Order.desc("activo"), Sort.Order.asc("nombre").ignoreCase());

	private final ServicioRepository servicios;
	private final BarberiaRepository barberias;
	private final BarberiaService barberiaService;

	public ServicioService(ServicioRepository servicios, BarberiaRepository barberias, BarberiaService barberiaService) {
		this.servicios = servicios;
		this.barberias = barberias;
		this.barberiaService = barberiaService;
	}

	@Transactional(readOnly = true)
	public List<ServicioResponse> listar(Long barberiaId, boolean incluirInactivos) {
		List<Servicio> lista = incluirInactivos ? servicios.findByBarberiaId(barberiaId, ACTIVOS_PRIMERO)
				: servicios.findByBarberiaIdAndActivoTrue(barberiaId, POR_NOMBRE);
		return lista.stream().map(ServicioResponse::de).toList();
	}

	@Transactional
	public ServicioResponse crear(Long barberiaId, CrearServicioRequest request) {
		String nombre = request.nombre().trim();
		if (servicios.existsByBarberiaIdAndActivoTrueAndNombreIgnoreCase(barberiaId, nombre)) {
			throw new ServicioDuplicadoException();
		}
		Servicio servicio = new Servicio(barberias.getReferenceById(barberiaId), nombre, request.duracionMin(),
				plata(request.precio()));
		servicio.setDescripcion(textoOpcional(request.descripcion()));
		return ServicioResponse.de(servicios.save(servicio));
	}

	@Transactional
	public ServicioResponse actualizar(Long barberiaId, Long id, ActualizarServicioRequest request) {
		Servicio servicio = buscar(barberiaId, id);
		if (request.nombre() != null) {
			String nombre = request.nombre().trim();
			if (servicio.isActivo() && nombreOcupado(barberiaId, nombre, id)) {
				throw new ServicioDuplicadoException();
			}
			servicio.setNombre(nombre);
		}
		if (request.descripcion() != null) {
			servicio.setDescripcion(textoOpcional(request.descripcion()));
		}
		if (request.duracionMin() != null) {
			servicio.setDuracionMin(request.duracionMin());
		}
		if (request.precio() != null) {
			servicio.setPrecio(plata(request.precio()));
		}
		return ServicioResponse.de(servicio);
	}

	/** Baja lógica: los turnos viejos siguen apuntando al servicio. */
	@Transactional
	public void darDeBaja(Long barberiaId, Long id) {
		buscar(barberiaId, id).setActivo(false);
	}

	@Transactional
	public ServicioResponse reactivar(Long barberiaId, Long id) {
		Servicio servicio = buscar(barberiaId, id);
		if (!servicio.isActivo()) {
			if (nombreOcupado(barberiaId, servicio.getNombre(), id)) {
				throw new ServicioDuplicadoException();
			}
			servicio.setActivo(true);
		}
		return ServicioResponse.de(servicio);
	}

	@Transactional(readOnly = true)
	public List<ServicioPublicoResponse> listarPublicos(String slug) {
		Long barberiaId = barberiaService.buscarPublica(slug).getId();
		return servicios.findByBarberiaIdAndActivoTrue(barberiaId, POR_NOMBRE).stream()
				.map(ServicioPublicoResponse::de)
				.toList();
	}

	private Servicio buscar(Long barberiaId, Long id) {
		return servicios.findByIdAndBarberiaId(id, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese servicio"));
	}

	private boolean nombreOcupado(Long barberiaId, String nombre, Long id) {
		return servicios.existsByBarberiaIdAndActivoTrueAndNombreIgnoreCaseAndIdNot(barberiaId, nombre, id);
	}

	/** Ya validado con @Digits(fraction = 2): el redondeo no cambia el valor, solo normaliza la escala. */
	private static BigDecimal plata(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP);
	}

	private static String textoOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

}
