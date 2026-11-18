package com.barberya.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.method.ParameterErrors;

import com.barberya.auth.CodigoInvalidoException;
import com.barberya.auth.CredencialesInvalidasException;
import com.barberya.auth.CuentaDuplicadaException;
import com.barberya.auth.EmailYaRegistradoException;
import com.barberya.auth.GoogleInvalidoException;
import com.barberya.auth.RefreshCookie;
import com.barberya.auth.SesionInvalidaException;
import com.barberya.auth.TokenRecuperacionInvalidoException;
import com.barberya.auth.UsuarioActual;
import com.barberya.barberia.SlugNoDisponibleException;
import com.barberya.barberia.YaTieneBarberiaException;
import com.barberya.servicio.ServicioDuplicadoException;

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

	/** Validación de los elementos de una lista en el body (ej. PUT /horarios): las claves quedan {@code [i].campo}. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ProblemDetail datosInvalidosEnLista(HandlerMethodValidationException error) {
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Revisá los datos ingresados");
		Map<String, String> errores = new LinkedHashMap<>();
		error.getParameterValidationResults().forEach(resultado -> {
			String prefijo = resultado.getContainerIndex() != null ? "[" + resultado.getContainerIndex() + "]" : "";
			if (resultado instanceof ParameterErrors errs) {
				for (FieldError campo : errs.getFieldErrors()) {
					errores.putIfAbsent(prefijo + "." + campo.getField(), campo.getDefaultMessage());
				}
			}
			else {
				resultado.getResolvableErrors().forEach(e -> errores.putIfAbsent(
						prefijo.isEmpty() ? resultado.getMethodParameter().getParameterName() : prefijo, e.getDefaultMessage()));
			}
		});
		problema.setProperty("errores", errores);
		return problema;
	}

	@ExceptionHandler(DatosInvalidosException.class)
	ProblemDetail datosInvalidosDeNegocio(DatosInvalidosException error) {
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", error.getMessage());
		problema.setProperty("errores", Map.of(error.getCampo(), error.getMessage()));
		return problema;
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ProblemDetail faltaParametro(MissingServletRequestParameterException error) {
		String mensaje = "Falta el parámetro " + error.getParameterName();
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", mensaje);
		problema.setProperty("errores", Map.of(error.getParameterName(), mensaje));
		return problema;
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException error) {
		String mensaje = "El parámetro " + error.getName() + " no es válido";
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", mensaje);
		problema.setProperty("errores", Map.of(error.getName(), mensaje));
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

	@ExceptionHandler(CuentaDuplicadaException.class)
	ProblemDetail cuentaDuplicada(CuentaDuplicadaException error) {
		return problema(HttpStatus.CONFLICT, "Mail ya registrado", error.getMessage());
	}

	@ExceptionHandler(GoogleInvalidoException.class)
	ProblemDetail googleInvalido(GoogleInvalidoException error) {
		return problema(HttpStatus.UNAUTHORIZED, "No autorizado", error.getMessage());
	}

	@ExceptionHandler(CodigoInvalidoException.class)
	ProblemDetail codigoInvalido(CodigoInvalidoException error) {
		return problema(HttpStatus.UNAUTHORIZED, "No autorizado", error.getMessage());
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

	/** Un usuario logueado que todavía no es miembro de ninguna barbería recibe un mensaje que le dice qué hacer. */
	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail sinPermiso(AccessDeniedException error) {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion instanceof JwtAuthenticationToken jwt && !UsuarioActual.tieneBarberia(jwt.getToken())) {
			return problema(HttpStatus.FORBIDDEN, "Sin barbería", "Primero creá tu barbería");
		}
		return problema(HttpStatus.FORBIDDEN, "Sin permiso", "No tenés permiso para hacer esto");
	}

	@ExceptionHandler(RecursoNoEncontradoException.class)
	ProblemDetail noEncontrado(RecursoNoEncontradoException error) {
		return problema(HttpStatus.NOT_FOUND, "No encontrado", error.getMessage());
	}

	@ExceptionHandler(YaTieneBarberiaException.class)
	ProblemDetail yaTieneBarberia(YaTieneBarberiaException error) {
		return problema(HttpStatus.CONFLICT, "Ya tenés una barbería", error.getMessage());
	}

	@ExceptionHandler(SlugNoDisponibleException.class)
	ProblemDetail slugNoDisponible(SlugNoDisponibleException error) {
		return problema(HttpStatus.CONFLICT, "Link no disponible", error.getMessage());
	}

	@ExceptionHandler(ServicioDuplicadoException.class)
	ProblemDetail servicioDuplicado(ServicioDuplicadoException error) {
		return problema(HttpStatus.CONFLICT, "Servicio repetido", error.getMessage());
	}

	private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
		problema.setTitle(titulo);
		return problema;
	}

}
