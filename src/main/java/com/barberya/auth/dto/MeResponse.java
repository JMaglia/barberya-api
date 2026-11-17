package com.barberya.auth.dto;

import com.barberya.barberia.RolMiembro;

/**
 * {@code barberiaId} y {@code rol} son null si el usuario no es miembro de ninguna barbería.
 * {@code perfilCompleto}: tiene nombre y celular; el front lo exige antes de dejar reservar.
 */
public record MeResponse(
		Long id,
		String nombre,
		String apellido,
		String email,
		String telefono,
		boolean emailVerificado,
		boolean perfilCompleto,
		Long barberiaId,
		RolMiembro rol) {
}
