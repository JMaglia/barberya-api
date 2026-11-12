package com.barberya.gasto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.barberya.barberia.Barberia;
import com.barberya.cobro.MetodoPago;

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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Las FK compuestas (categoría, gasto fijo y proveedor de la misma barbería) las garantiza la base.
 * El precio unitario no se guarda: se calcula como {@code monto / cantidad}.
 */
@Entity
@Table(name = "gastos")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Gasto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "barberia_id", nullable = false)
	private Barberia barberia;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "categoria_id")
	private CategoriaGasto categoria;

	/** Si está cargado, {@code esFijo} tiene que ser true (CHECK de la base). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "gasto_fijo_id")
	private GastoFijo gastoFijo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "proveedor_id")
	private Proveedor proveedor;

	/** Qué se compró (ej.: "Navajas"). */
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, length = 200)
	private String descripcion;

	@Positive
	private Integer cantidad;

	@NotNull
	@Positive
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@NotNull
	@Column(nullable = false)
	private LocalDate fecha;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private MetodoPago metodo;

	@Column(name = "comprobante_url")
	private String comprobanteUrl;

	@Column(name = "es_fijo", nullable = false)
	private boolean esFijo = false;

	@Generated(event = EventType.INSERT)
	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private Instant creadoEn;

	public Gasto(Barberia barberia, String descripcion, BigDecimal monto, LocalDate fecha) {
		this.barberia = barberia;
		this.descripcion = descripcion;
		this.monto = monto;
		this.fecha = fecha;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Gasto otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return Gasto.class.hashCode();
	}

}
