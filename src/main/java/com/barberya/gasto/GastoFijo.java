package com.barberya.gasto;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Plantilla de un gasto mensual. Está pendiente en un mes si no tiene un gasto asociado en ese mes. */
@Entity
@Table(name = "gastos_fijos")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GastoFijo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "barberia_id", nullable = false)
	private Barberia barberia;

	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, length = 200)
	private String concepto;

	@NotNull
	@Positive
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@NotNull
	@Min(1)
	@Max(28)
	@Column(name = "dia_del_mes", nullable = false)
	private Short diaDelMes;

	@Column(nullable = false)
	private boolean activo = true;

	@Generated(event = EventType.INSERT)
	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private Instant creadoEn;

	public GastoFijo(Barberia barberia, String concepto, BigDecimal monto, Short diaDelMes) {
		this.barberia = barberia;
		this.concepto = concepto;
		this.monto = monto;
		this.diaDelMes = diaDelMes;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof GastoFijo otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return GastoFijo.class.hashCode();
	}

}
