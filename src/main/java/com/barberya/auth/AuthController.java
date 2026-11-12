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

import com.barberya.auth.dto.LoginRequest;
import com.barberya.auth.dto.MensajeResponse;
import com.barberya.auth.dto.RecuperarRequest;
import com.barberya.auth.dto.RegistroRequest;
import com.barberya.auth.dto.RestablecerRequest;
import com.barberya.auth.dto.TokenResponse;
import com.barberya.auth.dto.UsuarioResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final RefreshCookie refreshCookie;

	public AuthController(AuthService authService, RefreshCookie refreshCookie) {
		this.authService = authService;
		this.refreshCookie = refreshCookie;
	}

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse registro(@Valid @RequestBody RegistroRequest request) {
		return authService.registrar(request);
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return conCookie(authService.login(request));
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
	public MensajeResponse recuperar(@Valid @RequestBody RecuperarRequest request) {
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
