package com.barberya.servicio;

import java.math.BigDecimal;

import com.barberya.barberia.Barberia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "servicios")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Servicio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "barberia_id", nullable = false)
	private Barberia barberia;

	@NotBlank
	@Size(max = 80)
	@Column(nullable = false, length = 80)
	private String nombre;

	private String descripcion;

	@NotNull
	@Positive
	@Column(name = "duracion_min", nullable = false)
	private Integer duracionMin;

	@NotNull
	@PositiveOrZero
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	@Column(nullable = false)
	private boolean activo = true;

	public Servicio(Barberia barberia, String nombre, Integer duracionMin, BigDecimal precio) {
		this.barberia = barberia;
		this.nombre = nombre;
		this.duracionMin = duracionMin;
		this.precio = precio;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Servicio otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return Servicio.class.hashCode();
	}

}
