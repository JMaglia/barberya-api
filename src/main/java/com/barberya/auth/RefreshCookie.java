package com.barberya.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Cookie httpOnly del refresh token, limitada a los endpoints de auth. */
@Component
public class RefreshCookie {

	public static final String NOMBRE = "refresh_token";
	public static final String PATH = "/api/v1/auth";

	/** Con {@code mantenerSesion} dura lo mismo que el token; sin él es una cookie de sesión. */
	public ResponseCookie crear(String valor, boolean mantenerSesion, Duration duracion) {
		ResponseCookie.ResponseCookieBuilder cookie = base(valor);
		if (mantenerSesion) {
			cookie.maxAge(duracion);
		}
		return cookie.build();
	}

	public ResponseCookie borrar() {
		return base("").maxAge(0).build();
	}

	private static ResponseCookie.ResponseCookieBuilder base(String valor) {
		return ResponseCookie.from(NOMBRE, valor)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(PATH);
	}

}
