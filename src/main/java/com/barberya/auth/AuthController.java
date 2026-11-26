package com.barberya.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.barberya.auth.dto.GoogleLoginRequest;
import com.barberya.auth.dto.LoginRequest;
import com.barberya.auth.dto.MensajeResponse;
import com.barberya.auth.dto.RecuperarRequest;
import com.barberya.auth.dto.RegistroRequest;
import com.barberya.auth.dto.RestablecerRequest;
import com.barberya.auth.dto.SolicitarCodigoRequest;
import com.barberya.auth.dto.TokenResponse;
import com.barberya.auth.dto.UsuarioResponse;
import com.barberya.auth.dto.VerificarCodigoRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Los límites de intentos se aplican acá, después de validar el body y antes de llamar al servicio (antes de tocar
 * la base o BCrypt). La IP es {@code getRemoteAddr()}: detrás de un proxy, ver {@code server.forward-headers-strategy}.
 */
@Tag(name = "Auth", description = "Registro, login (mail, Google, código), refresh, logout y recuperar clave")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final RefreshCookie refreshCookie;
	private final LimitesAuth limites;

	public AuthController(AuthService authService, RefreshCookie refreshCookie, LimitesAuth limites) {
		this.authService = authService;
		this.refreshCookie = refreshCookie;
		this.limites = limites;
	}

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse registro(@Valid @RequestBody RegistroRequest request, HttpServletRequest http) {
		limites.registro(http.getRemoteAddr());
		return authService.registrar(request);
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		limites.login(request.email(), http.getRemoteAddr());
		AuthService.Sesion sesion = authService.login(request);
		limites.loginExitoso(request.email());
		return conCookie(sesion);
	}

	@PostMapping("/google")
	public ResponseEntity<TokenResponse> google(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest http) {
		limites.google(http.getRemoteAddr());
		return conCookie(authService.loginGoogle(request));
	}

	/** Responde siempre 200 con el mismo mensaje, tenga cuenta o no el mail. */
	@PostMapping("/codigo/solicitar")
	public MensajeResponse solicitarCodigo(@Valid @RequestBody SolicitarCodigoRequest request, HttpServletRequest http) {
		limites.solicitarCodigo(request.email(), http.getRemoteAddr());
		authService.solicitarCodigo(request.email());
		return new MensajeResponse("Si el mail es válido, te enviamos un código para entrar");
	}

	@PostMapping("/codigo/verificar")
	public ResponseEntity<TokenResponse> verificarCodigo(@Valid @RequestBody VerificarCodigoRequest request,
			HttpServletRequest http) {
		limites.verificarCodigo(request.email(), http.getRemoteAddr());
		return conCookie(authService.verificarCodigo(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(
			@CookieValue(name = RefreshCookie.NOMBRE, required = false) String refresh) {
		return conCookie(authService.refrescar(refresh));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NOMBRE, required = false) String refresh) {
		authService.logout(refresh);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, refreshCookie.borrar().toString())
				.build();
	}

	/** Responde siempre 200, exista o no el mail. */
	@PostMapping("/recuperar")
	public MensajeResponse recuperar(@Valid @RequestBody RecuperarRequest request, HttpServletRequest http) {
		limites.recuperar(request.email(), http.getRemoteAddr());
		authService.recuperar(request.email());
		return new MensajeResponse("Si el mail está registrado, te enviamos un link para restablecer la contraseña");
	}

	@PostMapping("/restablecer")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restablecer(@Valid @RequestBody RestablecerRequest request) {
		authService.restablecer(request);
	}

	private static ResponseEntity<TokenResponse> conCookie(AuthService.Sesion sesion) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, sesion.cookie().toString())
				.body(sesion.token());
	}

}
