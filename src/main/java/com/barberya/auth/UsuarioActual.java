package com.barberya.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** Lee el usuario y la barbería del access token. El {@code barberiaId} nunca sale del front. */
public final class UsuarioActual {

	public static final String CLAIM_BARBERIA = "barberiaId";
	public static final String CLAIM_ROL = "rol";

	private UsuarioActual() {
	}

	public static Long usuarioId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

	/** Las rutas que lo usan exigen ROLE_DUENO, y ese claim viene siempre junto con barberiaId. */
	public static Long barberiaId(Jwt jwt) {
		Object valor = jwt.getClaims().get(CLAIM_BARBERIA);
		if (valor instanceof Number numero) {
			return numero.longValue();
		}
		throw new IllegalStateException("El token no tiene barberiaId");
	}

	public static boolean tieneBarberia(Jwt jwt) {
		return jwt.getClaims().get(CLAIM_BARBERIA) != null;
	}

}
