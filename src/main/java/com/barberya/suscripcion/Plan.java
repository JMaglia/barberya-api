package com.barberya.suscripcion;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "planes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 50)
	@Column(nullable = false, length = 50)
	private String nombre;

	@NotNull
	@PositiveOrZero
	@Column(name = "precio_mensual", nullable = false, precision = 12, scale = 2)
	private BigDecimal precioMensual;

	@NotNull
	@Positive
	@Column(name = "max_barberos", nullable = false)
	private Integer maxBarberos;

	@Column(nullable = false)
	private boolean activo = true;

	public Plan(String nombre, BigDecimal precioMensual, Integer maxBarberos) {
		this.nombre = nombre;
		this.precioMensual = precioMensual;
		this.maxBarberos = maxBarberos;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Plan otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return Plan.class.hashCode();
	}

}
