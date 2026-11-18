package com.barberya.barberia;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.auth.UsuarioActual;

/**
 * El miembro activo del usuario del token dentro de la barbería del token. Agenda, disponibilidad y turnos
 * cuelgan de él; si no existe (lo dieron de baja, o el token quedó viejo) → 403.
 */
@Component
public class MiembroActual {

	private final MiembroBarberiaRepository miembros;

	public MiembroActual(MiembroBarberiaRepository miembros) {
		this.miembros = miembros;
	}

	@Transactional(readOnly = true)
	public MiembroBarberia obtener(Jwt jwt) {
		return miembros
				.findByUsuarioIdAndBarberiaIdAndActivoTrue(UsuarioActual.usuarioId(jwt), UsuarioActual.barberiaId(jwt))
				.orElseThrow(() -> new AccessDeniedException("No es miembro activo de la barbería del token"));
	}

}
