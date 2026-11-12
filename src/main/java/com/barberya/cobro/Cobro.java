package com.barberya.cobro;

import java.math.BigDecimal;
import java.time.Instant;

import com.barberya.barberia.Barberia;
import com.barberya.turno.Turno;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Se crea solo al completar el turno, sin medio de pago. */
@Entity
@Table(name = "cobros")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cobro {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "barberia_id", nullable = false)
	private Barberia barberia;

	/** Único: un turno se cobra una sola vez. NULL queda para ventas sueltas futuras. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "turno_id")
	private Turno turno;

	@NotNull
	@PositiveOrZero
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private MetodoPago metodo;

	@NotNull
	@Column(name = "cobrado_en", nullable = false)
	private Instant cobradoEn;

	@Size(max = 120)
	@Column(length = 120)
	private String concepto;

	public Cobro(Barberia barberia, BigDecimal monto, Instant cobradoEn) {
		this.barberia = barberia;
		this.monto = monto;
		this.cobradoEn = cobradoEn;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Cobro otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return Cobro.class.hashCode();
	}

}
