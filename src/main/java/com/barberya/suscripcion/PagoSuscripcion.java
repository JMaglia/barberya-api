package com.barberya.suscripcion;

import java.math.BigDecimal;
import java.time.Instant;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pagos_suscripcion")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PagoSuscripcion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "suscripcion_id", nullable = false)
	private Suscripcion suscripcion;

	@NotNull
	@PositiveOrZero
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@NotNull
	@Column(name = "pagado_en", nullable = false)
	private Instant pagadoEn;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoPagoSuscripcion estado;

	/** Id del pago en Mercado Pago; da la idempotencia del webhook. */
	@NotBlank
	@Size(max = 100)
	@Column(name = "referencia_externa", nullable = false, length = 100)
	private String referenciaExterna;

	public PagoSuscripcion(Suscripcion suscripcion, BigDecimal monto, Instant pagadoEn,
			EstadoPagoSuscripcion estado, String referenciaExterna) {
		this.suscripcion = suscripcion;
		this.monto = monto;
		this.pagadoEn = pagadoEn;
		this.estado = estado;
		this.referenciaExterna = referenciaExterna;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof PagoSuscripcion otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return PagoSuscripcion.class.hashCode();
	}

}
