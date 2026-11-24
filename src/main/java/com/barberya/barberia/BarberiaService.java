package com.barberya.barberia;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.auth.TokenService;
import com.barberya.auth.Usuario;
import com.barberya.auth.UsuarioRepository;
import com.barberya.barberia.dto.ActualizarBarberiaRequest;
import com.barberya.barberia.dto.BarberiaCreadaResponse;
import com.barberya.barberia.dto.BarberiaPublicaResponse;
import com.barberya.barberia.dto.BarberiaResponse;
import com.barberya.barberia.dto.CrearBarberiaRequest;
import com.barberya.error.RecursoNoEncontradoException;
import com.barberya.error.Restricciones;

@Service
public class BarberiaService {

	private static final String RESTRICCION_SLUG_UNICO = "barberias_slug_key";

	private final BarberiaRepository barberias;
	private final MiembroBarberiaRepository miembros;
	private final UsuarioRepository usuarios;
	private final TokenService tokenService;

	public BarberiaService(BarberiaRepository barberias, MiembroBarberiaRepository miembros,
			UsuarioRepository usuarios, TokenService tokenService) {
		this.barberias = barberias;
		this.miembros = miembros;
		this.usuarios = usuarios;
		this.tokenService = tokenService;
	}

	/**
	 * Onboarding: crea la barbería y al usuario como DUENO, y devuelve un access token que ya trae barberiaId y rol.
	 * La fila del usuario queda bloqueada hasta el commit, así dos pedidos simultáneos no crean dos barberías.
	 */
	@Transactional
	public BarberiaCreadaResponse crear(Long usuarioId, CrearBarberiaRequest request) {
		Usuario usuario = usuarios.findByIdParaActualizar(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos tu cuenta"));
		if (miembros.existsByUsuarioId(usuarioId)) {
			throw new YaTieneBarberiaException();
		}
		String nombre = request.nombre().trim();
		String slug = request.slug() != null ? verificarSlugLibre(request.slug(), null)
				: SlugGenerador.generar(nombre, barberias::existsBySlug);

		Barberia barberia = new Barberia(nombre, slug);
		barberia.setTelefono(request.telefono().trim());
		barberia.setDireccion(textoOpcional(request.direccion()));
		if (request.zonaHoraria() != null) {
			barberia.setZonaHoraria(request.zonaHoraria());
		}
		barberia = guardar(barberia);
		miembros.saveAndFlush(new MiembroBarberia(barberia, usuario, RolMiembro.DUENO));

		return new BarberiaCreadaResponse(BarberiaResponse.de(barberia), tokenService.generarAccess(usuario));
	}

	@Transactional(readOnly = true)
	public BarberiaResponse obtener(Long barberiaId) {
		return BarberiaResponse.de(buscar(barberiaId));
	}

	@Transactional
	public BarberiaResponse actualizar(Long barberiaId, ActualizarBarberiaRequest request) {
		Barberia barberia = buscar(barberiaId);
		if (request.nombre() != null) {
			barberia.setNombre(request.nombre().trim());
		}
		if (request.telefono() != null) {
			barberia.setTelefono(request.telefono().trim());
		}
		if (request.direccion() != null) {
			barberia.setDireccion(textoOpcional(request.direccion()));
		}
		if (request.slug() != null && !request.slug().equals(barberia.getSlug())) {
			barberia.setSlug(verificarSlugLibre(request.slug(), barberiaId));
		}
		if (request.zonaHoraria() != null) {
			barberia.setZonaHoraria(request.zonaHoraria());
		}
		if (request.cancelarHastaHoras() != null) {
			barberia.setCancelarHastaHoras(request.cancelarHastaHoras());
		}
		if (request.reservaMaxDias() != null) {
			barberia.setReservaMaxDias(request.reservaMaxDias());
		}
		if (request.reservaMinMinutos() != null) {
			barberia.setReservaMinMinutos(request.reservaMinMinutos());
		}
		if (request.pausaEntreTurnosMin() != null) {
			barberia.setPausaEntreTurnosMin(request.pausaEntreTurnosMin());
		}
		return BarberiaResponse.de(guardar(barberia));
	}

	/** Para el link público: solo barberías activas. */
	@Transactional(readOnly = true)
	public Barberia buscarPublica(String slug) {
		return barberias.findBySlugAndActivaTrue(slug)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos esa barbería"));
	}

	@Transactional(readOnly = true)
	public BarberiaPublicaResponse obtenerPublica(String slug) {
		return BarberiaPublicaResponse.de(buscarPublica(slug));
	}

	private Barberia buscar(Long barberiaId) {
		return barberias.findById(barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos tu barbería"));
	}

	/** {@code propia}: id de la barbería que ya lo usa y lo puede conservar; null al crear. */
	private String verificarSlugLibre(String slug, Long propia) {
		if (SlugsReservados.esReservado(slug)) {
			throw SlugNoDisponibleException.reservado();
		}
		boolean ocupado = propia == null ? barberias.existsBySlug(slug) : barberias.existsBySlugAndIdNot(slug, propia);
		if (ocupado) {
			throw SlugNoDisponibleException.ocupado();
		}
		return slug;
	}

	/** Dos barberías que toman el mismo slug a la vez: la segunda choca con el UNIQUE → 409. */
	private Barberia guardar(Barberia barberia) {
		try {
			return barberias.saveAndFlush(barberia);
		} catch (DataIntegrityViolationException e) {
			if (Restricciones.es(e, RESTRICCION_SLUG_UNICO)) {
				throw SlugNoDisponibleException.ocupado();
			}
			throw e;
		}
	}

	private static String textoOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

}
