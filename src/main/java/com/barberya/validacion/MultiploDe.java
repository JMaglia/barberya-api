package com.barberya.validacion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** El número es múltiplo de {@code value}. Null es válido. */
@Documented
@Constraint(validatedBy = MultiploDe.Validador.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface MultiploDe {

	int value();

	String message() default "Tiene que ser múltiplo de {value}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validador implements ConstraintValidator<MultiploDe, Integer> {

		private int divisor;

		@Override
		public void initialize(MultiploDe anotacion) {
			this.divisor = anotacion.value();
		}

		@Override
		public boolean isValid(Integer valor, ConstraintValidatorContext context) {
			return valor == null || valor % divisor == 0;
		}

	}

}
