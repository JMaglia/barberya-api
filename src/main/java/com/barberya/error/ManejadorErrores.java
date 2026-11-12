package com.barberya.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.barberya.auth.CredencialesInvalidasException;
import com.barberya.auth.EmailYaRegistradoException;
import com.barberya.auth.RefreshCookie;
import com.barberya.auth.SesionInvalidaException;
import com.barberya.auth.TokenRecuperacionInvalidoException;

/** Errores en formato ProblemDetail (RFC 7807), con mensaje en español. */
@RestControllerAdvice
public class ManejadorErrores {

	private final RefreshCookie refreshCookie;

	public ManejadorErrores(RefreshCookie refreshCookie) {
		this.refreshCookie = refreshCookie;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail datosInvalidos(MethodArgumentNotValidException error) {
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Revisá los datos ingresados");
		Map<String, String> errores = new LinkedHashMap<>();
		error.getBindingResult().getFieldErrors()
				.forEach(campo -> errores.putIfAbsent(campo.getField(), campo.getDefaultMessage()));
		problema.setProperty("errores", errores);
		return problema;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail cuerpoIlegible(HttpMessageNotReadableException error) {
		return problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "El cuerpo de la solicitud no es válido");
	}

	@ExceptionHandler(EmailYaRegistradoException.class)
	ProblemDetail emailYaRegistrado(EmailYaRegistradoException error) {
		return problema(HttpStatus.CONFLICT, "Mail ya registrado", error.getMessage());
	}

	@ExceptionHandler(CredencialesInvalidasException.class)
	ProblemDetail credencialesInvalidas(CredencialesInvalidasException error) {
		return problema(HttpStatus.UNAUTHORIZED, "No autorizado", error.getMessage());
	}

	/** Además del 401, borra la cookie del refresh, que ya no sirve. */
	@ExceptionHandler(SesionInvalidaException.class)
	ResponseEntity<ProblemDetail> sesionInvalida(SesionInvalidaException error) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.SET_COOKIE, refreshCookie.borrar().toString())
				.body(problema(HttpStatus.UNAUTHORIZED, "Sesión vencida", error.getMessage()));
	}

	@ExceptionHandler(TokenRecuperacionInvalidoException.class)
	ProblemDetail tokenRecuperacionInvalido(TokenRecuperacionInvalidoException error) {
		return problema(HttpStatus.BAD_REQUEST, "Link inválido", error.getMessage());
	}

	/** Sin token o con un token inválido o vencido. */
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail sinAutenticar(AuthenticationException error) {
		return problema(HttpStatus.UNAUTHORIZED, "No autorizado", "Tenés que iniciar sesión");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail sinPermiso(AccessDeniedException error) {
		return problema(HttpStatus.FORBIDDEN, "Sin permiso", "No tenés permiso para hacer esto");
	}

	private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
		problema.setTitle(titulo);
		return problema;
	}

}
