package com.barberya.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.auth.dto.ActualizarPerfilRequest;
import com.barberya.auth.dto.GoogleLoginRequest;
import com.barberya.auth.dto.LoginRequest;
import com.barberya.auth.dto.MeResponse;
import com.barberya.auth.dto.RegistroRequest;
import com.barberya.auth.dto.RestablecerRequest;
import com.barberya.auth.dto.TokenResponse;
import com.barberya.auth.dto.UsuarioResponse;
import com.barberya.auth.dto.VerificarCodigoRequest;
import com.barberya.barberia.MiembroBarberia;
import com.barberya.barberia.MiembroBarberiaRepository;
import com.barberya.config.BarberyaProperties;

@Service
@Transactional
public class AuthService {

	static final Duration REFRESH_MANTENER_SESION = Duration.ofDays(30);
	static final Duration REFRESH_DE_SESION = Duration.ofDays(1);
	static final Duration TOKEN_RECUPERACION = Duration.ofMinutes(30);
	static final Duration CODIGO_LOGIN = Duration.ofMinutes(10);
	/** Con esta cantidad de intentos fallidos el código ya no sirve. */
	static final int MAX_INTENTOS_CODIGO = 5;

	private static final String RESTRICCION_EMAIL_UNICO = "usuarios_email_key";
	private static final Set<String> RESTRICCIONES_CUENTA_UNICA = Set.of(RESTRICCION_EMAIL_UNICO, "usuarios_google_sub_key");

	private final UsuarioRepository usuarios;
	private final RefreshTokenRepository refreshTokens;
	private final TokenVerificacionRepository tokensVerificacion;
	private final MiembroBarberiaRepository miembros;
	private final TokenService tokenService;
	private final RefreshCookie refreshCookie;
	private final PasswordEncoder passwordEncoder;
	private final EnvioMails envioMails;
	private final VerificadorGoogle verificadorGoogle;
	private final String frontUrl;
	/** Se compara contra este hash cuando el mail no existe, para tardar lo mismo y no revelarlo. */
	private final String hashDeRelleno;

	public AuthService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens,
			TokenVerificacionRepository tokensVerificacion, MiembroBarberiaRepository miembros,
			TokenService tokenService, RefreshCookie refreshCookie, PasswordEncoder passwordEncoder,
			EnvioMails envioMails, VerificadorGoogle verificadorGoogle, BarberyaProperties props) {
		this.usuarios = usuarios;
		this.refreshTokens = refreshTokens;
		this.tokensVerificacion = tokensVerificacion;
		this.miembros = miembros;
		this.tokenService = tokenService;
		this.refreshCookie = refreshCookie;
		this.passwordEncoder = passwordEncoder;
		this.envioMails = envioMails;
		this.verificadorGoogle = verificadorGoogle;
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
			if (RESTRICCION_EMAIL_UNICO.equals(restriccionViolada(e))) {
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
	 * Busca el usuario por su cuenta de Google; si no, por el mail (y le vincula la cuenta); si no, lo crea.
	 * Google ya verificó el mail.
	 */
	public Sesion loginGoogle(GoogleLoginRequest request) {
		DatosGoogle datos = verificadorGoogle.verificar(request.idToken());
		String email = normalizarEmail(datos.email());

		Usuario porSub = usuarios.findByGoogleSub(datos.sub()).orElse(null);
		if (porSub != null) {
			return crearSesion(porSub, request.mantenerSesion()).sesion();
		}
		Usuario existente = usuarios.findByEmail(email).orElse(null);
		if (existente == null) {
			Usuario nuevo = new Usuario(email, datos.nombre());
			nuevo.setApellido(datos.apellido());
			nuevo.setGoogleSub(datos.sub());
			nuevo.setEmailVerificado(true);
			return crearSesion(guardarCuenta(nuevo), request.mantenerSesion()).sesion();
		}
		if (existente.getGoogleSub() != null) {
			// El mail ya está vinculado a otra cuenta de Google.
			throw new GoogleInvalidoException();
		}
		existente.setGoogleSub(datos.sub());
		boolean borroPassword = confirmarEmail(existente);
		return iniciarSesionVerificada(guardarCuenta(existente), borroPassword, request.mantenerSesion());
	}

	/**
	 * Vence los códigos anteriores del mail y manda uno nuevo. Se genera para cualquier mail, tenga cuenta o no
	 * (el usuario se crea al verificarlo), así el endpoint responde igual en los dos casos.
	 */
	public void solicitarCodigo(String emailIngresado) {
		String email = normalizarEmail(emailIngresado);
		Instant ahora = Instant.now();
		tokensVerificacion.vencerVigentes(email, TipoTokenVerificacion.CODIGO_LOGIN, ahora);
		String codigo = tokenService.nuevoCodigo();
		TokenVerificacion token = new TokenVerificacion(email, TipoTokenVerificacion.CODIGO_LOGIN,
				TokenService.sha256(codigo), ahora.plus(CODIGO_LOGIN));
		Usuario usuario = usuarios.findByEmail(email).orElse(null);
		token.setUsuario(usuario);
		tokensVerificacion.save(token);
		envioMails.enviarCodigo(email, usuario != null ? usuario.getNombre() : null, codigo);
	}

	/**
	 * Un solo uso, hasta {@value #MAX_INTENTOS_CODIGO} intentos. {@code noRollbackFor}: el intento fallido se commitea
	 * aunque se responda 401.
	 */
	@Transactional(noRollbackFor = CodigoInvalidoException.class)
	public Sesion verificarCodigo(VerificarCodigoRequest request) {
		String email = normalizarEmail(request.email());
		Instant ahora = Instant.now();
		TokenVerificacion token = tokensVerificacion
				.findFirstByEmailAndTipoAndUsadoEnIsNullOrderByIdDesc(email, TipoTokenVerificacion.CODIGO_LOGIN)
				.filter(t -> t.getExpiraEn().isAfter(ahora))
				.filter(t -> t.getIntentos() < MAX_INTENTOS_CODIGO)
				.orElseThrow(CodigoInvalidoException::new);
		if (!iguales(TokenService.sha256(request.codigo()), token.getTokenHash())) {
			token.setIntentos((short) (token.getIntentos() + 1));
			throw new CodigoInvalidoException();
		}
		token.setUsadoEn(ahora);

		Usuario usuario = usuarios.findByEmail(email).orElse(null);
		boolean borroPassword = false;
		if (usuario == null) {
			Usuario nuevo = new Usuario(email, null);
			nuevo.setEmailVerificado(true);
			usuario = guardarCuenta(nuevo);
		} else {
			borroPassword = confirmarEmail(usuario);
		}
		token.setUsuario(usuario);
		return iniciarSesionVerificada(usuario, borroPassword, request.mantenerSesion());
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
			envioMails.enviarRecuperacionClave(usuario.getEmail(), usuario.getNombre(), frontUrl + "/restablecer?token=" + valor);
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
		boolean perfilCompleto = tieneTexto(usuario.getNombre()) && tieneTexto(usuario.getTelefono());
		return new MeResponse(usuario.getId(), usuario.getNombre(), usuario.getApellido(), usuario.getEmail(),
				usuario.getTelefono(), usuario.isEmailVerificado(), perfilCompleto,
				miembro != null ? miembro.getBarberia().getId() : null,
				miembro != null ? miembro.getRol() : null);
	}

	/** El usuario sale del token: no se puede tocar el perfil de otro. */
	public MeResponse actualizarPerfil(Long usuarioId, ActualizarPerfilRequest request) {
		Usuario usuario = usuarios.findById(usuarioId).orElseThrow(SesionInvalidaException::new);
		usuario.setNombre(request.nombre().trim());
		usuario.setApellido(tieneTexto(request.apellido()) ? request.apellido().trim() : null);
		usuario.setTelefono(request.telefono().trim());
		return me(usuarioId);
	}

	private RefreshToken buscarRefresh(String valorCookie) {
		if (valorCookie == null || valorCookie.isBlank()) {
			throw new SesionInvalidaException();
		}
		return refreshTokens.findByTokenHash(TokenService.sha256(valorCookie))
				.orElseThrow(SesionInvalidaException::new);
	}

	/**
	 * Marca el mail como verificado. Si no lo estaba y la cuenta tenía contraseña, la borra: la puso alguien que nunca
	 * probó ser dueño del mail (toma de cuenta anticipada). Devuelve true si la borró.
	 */
	private static boolean confirmarEmail(Usuario usuario) {
		if (usuario.isEmailVerificado()) {
			return false;
		}
		usuario.setEmailVerificado(true);
		if (usuario.getPasswordHash() == null) {
			return false;
		}
		usuario.setPasswordHash(null);
		return true;
	}

	/**
	 * Si se borró la contraseña, también se cierran las sesiones abiertas con ella. Va al final porque
	 * {@code revocarTodosDelUsuario} hace flush y limpia el contexto: lo cargado antes queda separado.
	 */
	private Sesion iniciarSesionVerificada(Usuario usuario, boolean borroPassword, boolean mantenerSesion) {
		if (borroPassword) {
			refreshTokens.revocarTodosDelUsuario(usuario.getId());
		}
		return crearSesion(usuario, mantenerSesion).sesion();
	}

	/** Dos altas simultáneas del mismo mail o de la misma cuenta de Google: la segunda choca con el UNIQUE → 409. */
	private Usuario guardarCuenta(Usuario usuario) {
		try {
			return usuarios.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			String restriccion = restriccionViolada(e);
			if (restriccion != null && RESTRICCIONES_CUENTA_UNICA.contains(restriccion)) {
				throw new CuentaDuplicadaException();
			}
			throw e;
		}
	}

	private SesionCreada crearSesion(Usuario usuario, boolean mantenerSesion) {
		Duration duracion = mantenerSesion ? REFRESH_MANTENER_SESION : REFRESH_DE_SESION;
		String valor = tokenService.nuevoValorAleatorio();
		RefreshToken refresh = refreshTokens.save(new RefreshToken(usuario, TokenService.sha256(valor),
				Instant.now().plus(duracion), mantenerSesion));
		ResponseCookie cookie = refreshCookie.crear(valor, mantenerSesion, duracion);
		return new SesionCreada(new Sesion(tokenService.generarAccess(usuario), cookie), refresh);
	}

	private static String restriccionViolada(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException cve ? cve.getConstraintName() : null;
	}

	/** En tiempo constante, para no dar pistas de cuántos caracteres coinciden. */
	private static boolean iguales(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean tieneTexto(String valor) {
		return valor != null && !valor.isBlank();
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
