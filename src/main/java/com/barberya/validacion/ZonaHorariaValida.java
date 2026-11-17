package com.barberya.validacion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Un id de zona de la base IANA (ej.: America/Argentina/Buenos_Aires). Null es válido. */
@Documented
@Constraint(validatedBy = ZonaHorariaValida.Validador.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ZonaHorariaValida {

	String message() default "La zona horaria no es válida";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validador implements ConstraintValidator<ZonaHorariaValida, String> {

		// Sin offsets sueltos ("-03:00") ni "Z": solo ids de región.
		private static final Set<String> ZONAS = ZoneId.getAvailableZoneIds();

		@Override
		public boolean isValid(String valor, ConstraintValidatorContext context) {
			return valor == null || (valor.length() <= 50 && ZONAS.contains(valor));
		}

	}

}
