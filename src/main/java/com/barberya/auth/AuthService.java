package com.barberya.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.auth.dto.LoginRequest;
import com.barberya.auth.dto.MeResponse;
import com.barberya.auth.dto.RegistroRequest;
import com.barberya.auth.dto.RestablecerRequest;
import com.barberya.auth.dto.TokenResponse;
import com.barberya.auth.dto.UsuarioResponse;
import com.barberya.barberia.MiembroBarberia;
import com.barberya.barberia.MiembroBarberiaRepository;
import com.barberya.config.BarberyaProperties;

@Service
@Transactional
public class AuthService {

	static final Duration REFRESH_MANTENER_SESION = Duration.ofDays(30);
	static final Duration REFRESH_DE_SESION = Duration.ofDays(1);
	static final Duration TOKEN_RECUPERACION = Duration.ofMinutes(30);

	private static final String RESTRICCION_EMAIL_UNICO = "usuarios_email_key";

	private final UsuarioRepository usuarios;
	private final RefreshTokenRepository refreshTokens;
	private final TokenVerificacionRepository tokensVerificacion;
	private final MiembroBarberiaRepository miembros;
	private final TokenService tokenService;
	private final RefreshCookie refreshCookie;
	private final PasswordEncoder passwordEncoder;
	private final EnvioMails envioMails;
	private final String frontUrl;
	/** Se compara contra este hash cuando el mail no existe, para tardar lo mismo y no revelarlo. */
	private final String hashDeRelleno;

	public AuthService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens,
			TokenVerificacionRepository tokensVerificacion, MiembroBarberiaRepository miembros,
			TokenService tokenService, RefreshCookie refreshCookie, PasswordEncoder passwordEncoder,
			EnvioMails envioMails, BarberyaProperties props) {
		this.usuarios = usuarios;
		this.refreshTokens = refreshTokens;
		this.tokensVerificacion = tokensVerificacion;
		this.miembros = miembros;
		this.tokenService = tokenService;
		this.refreshCookie = refreshCookie;
		this.passwordEncoder = passwordEncoder;
		this.envioMails = envioMails;
		this.frontUrl = props.frontUrl();
		this.hashDeRelleno = passwordEncoder.encode(tokenService.nuevoValorAleatorio());
	}

	public UsuarioResponse registrar(RegistroRequest request) {
		String email = normalizarEmail(request.email());
		if (usuarios.existsByEmail(email)) {
			throw new EmailYaRegistradoException();
		}
		Usuario usuario = new Usuario(email, request.nombre().trim());
		usuario.setTelefono(request.telefono().trim());
		usuario.setPasswordHash(passwordEncoder.encode(request.password()));
		try {
			return UsuarioResponse.de(usuarios.saveAndFlush(usuario));
		} catch (DataIntegrityViolationException e) {
			// Dos registros simultáneos con el mismo mail: el segundo choca con el UNIQUE.
			if (e.getCause() instanceof ConstraintViolationException cve
					&& RESTRICCION_EMAIL_UNICO.equals(cve.getConstraintName())) {
				throw new EmailYaRegistradoException();
			}
			throw e;
		}
	}

	public Sesion login(LoginRequest request) {
		Usuario usuario = usuarios.findByEmail(normalizarEmail(request.email())).orElse(null);
		String hash = usuario != null && usuario.getPasswordHash() != null ? usuario.getPasswordHash() : hashDeRelleno;
		boolean coincide = passwordEncoder.matches(request.password(), hash);
		if (usuario == null || usuario.getPasswordHash() == null || !coincide) {
			throw new CredencialesInvalidasException();
		}
		return crearSesion(usuario, request.mantenerSesion()).sesion();
	}

	/**
	 * Rota el refresh: el usado se revoca y apunta al nuevo. Si llega uno ya revocado (posible robo),
	 * se revocan todos los del usuario. {@code noRollbackFor}: esa revocación se commitea aunque se responda 401.
	 */
	@Transactional(noRollbackFor = SesionInvalidaException.class)
	public Sesion refrescar(String valorCookie) {
		RefreshToken actual = buscarRefresh(valorCookie);
		if (actual.isRevocado()) {
			refreshTokens.revocarTodosDelUsuario(actual.getUsuario().getId());
			throw new SesionInvalidaException();
		}
		if (!actual.getExpiraEn().isAfter(Instant.now())) {
			throw new SesionInvalidaException();
		}
		actual.setRevocado(true);
		SesionCreada nueva = crearSesion(actual.getUsuario(), actual.isMantenerSesion());
		actual.setReemplazadoPor(nueva.refreshToken());
		return nueva.sesion();
	}

	public void logout(String valorCookie) {
		if (valorCookie == null || valorCookie.isBlank()) {
			return;
		}
		refreshTokens.findByTokenHash(TokenService.sha256(valorCookie))
				.ifPresent(token -> token.setRevocado(true));
	}

	/** Si el mail no existe no hace nada: el endpoint responde igual, para no revelarlo. */
	public void recuperar(String emailIngresado) {
		usuarios.findByEmail(normalizarEmail(emailIngresado)).ifPresent(usuario -> {
			String valor = tokenService.nuevoValorAleatorio();
			TokenVerificacion token = new TokenVerificacion(usuario.getEmail(), TipoTokenVerificacion.RECUPERAR_CLAVE,
					TokenService.sha256(valor), Instant.now().plus(TOKEN_RECUPERACION));
			token.setUsuario(usuario);
			tokensVerificacion.save(token);
			envioMails.enviarRecuperacionClave(usuario.getEmail(), frontUrl + "/restablecer?token=" + valor);
		});
	}

	/** Un solo uso. Revoca todas las sesiones del usuario. */
	public void restablecer(RestablecerRequest request) {
		TokenVerificacion token = tokensVerificacion
				.findFirstByTokenHashAndTipoAndUsadoEnIsNull(TokenService.sha256(request.token()),
						TipoTokenVerificacion.RECUPERAR_CLAVE)
				.filter(t -> t.getExpiraEn().isAfter(Instant.now()))
				.filter(t -> t.getUsuario() != null)
				.orElseThrow(TokenRecuperacionInvalidoException::new);
		token.setUsadoEn(Instant.now());
		Usuario usuario = token.getUsuario();
		usuario.setPasswordHash(passwordEncoder.encode(request.nuevaPassword()));
		refreshTokens.revocarTodosDelUsuario(usuario.getId());
	}

	@Transactional(readOnly = true)
	public MeResponse me(Long usuarioId) {
		Usuario usuario = usuarios.findById(usuarioId).orElseThrow(SesionInvalidaException::new);
		MiembroBarberia miembro = miembros.findFirstByUsuarioIdAndActivoTrue(usuarioId).orElse(null);
		return new MeResponse(usuario.getId(), usuario.getNombre(), usuario.getApellido(), usuario.getEmail(),
				usuario.getTelefono(), usuario.isEmailVerificado(),
				miembro != null ? miembro.getBarberia().getId() : null,
				miembro != null ? miembro.getRol() : null);
	}

	private RefreshToken buscarRefresh(String valorCookie) {
		if (valorCookie == null || valorCookie.isBlank()) {
			throw new SesionInvalidaException();
		}
		return refreshTokens.findByTokenHash(TokenService.sha256(valorCookie))
				.orElseThrow(SesionInvalidaException::new);
	}

	private SesionCreada crearSesion(Usuario usuario, boolean mantenerSesion) {
		Duration duracion = mantenerSesion ? REFRESH_MANTENER_SESION : REFRESH_DE_SESION;
		String valor = tokenService.nuevoValorAleatorio();
		RefreshToken refresh = refreshTokens.save(new RefreshToken(usuario, TokenService.sha256(valor),
				Instant.now().plus(duracion), mantenerSesion));
		ResponseCookie cookie = refreshCookie.crear(valor, mantenerSesion, duracion);
		return new SesionCreada(new Sesion(tokenService.generarAccess(usuario), cookie), refresh);
	}

	private static String normalizarEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	/** Lo que el controller devuelve: el access en el body y el refresh en la cookie. */
	public record Sesion(TokenResponse token, ResponseCookie cookie) {

		@Override
		public String toString() {
			return "Sesion[]";
		}
	}

	private record SesionCreada(Sesion sesion, RefreshToken refreshToken) {
	}

}
