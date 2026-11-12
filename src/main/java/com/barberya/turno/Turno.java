package com.barberya.turno;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.barberya.barberia.Barberia;
import com.barberya.barberia.MiembroBarberia;
import com.barberya.cliente.Cliente;
import com.barberya.servicio.Servicio;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Las FK compuestas (miembro, cliente y servicio de la misma barbería) y la no superposición
 * ({@code turnos_sin_superposicion}) las garantiza la base.
 */
@Entity
@Table(name = "turnos")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Turno {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "barberia_id", nullable = false)
	private Barberia barberia;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "miembro_id", nullable = false)
	private MiembroBarberia miembro;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "servicio_id", nullable = false)
	private Servicio servicio;

	@NotNull
	@Column(nullable = false)
	private Instant inicio;

	/** Inicio + duración del servicio. */
	@NotNull
	@Column(nullable = false)
	private Instant fin;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoTurno estado = EstadoTurno.CONFIRMADO;

	/** Copia del precio del servicio al reservar. */
	@NotNull
	@PositiveOrZero
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	private String notas;

	@Column(name = "cancelado_en")
	private Instant canceladoEn;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancelado_por", length = 20)
	private CanceladoPor canceladoPor;

	@Column(name = "recordatorio_enviado_en")
	private Instant recordatorioEnviadoEn;

	@Generated(event = EventType.INSERT)
	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private Instant creadoEn;

	public Turno(Barberia barberia, MiembroBarberia miembro, Cliente cliente, Servicio servicio,
			Instant inicio, Instant fin, BigDecimal precio) {
		this.barberia = barberia;
		this.miembro = miembro;
		this.cliente = cliente;
		this.servicio = servicio;
		this.inicio = inicio;
		this.fin = fin;
		this.precio = precio;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Turno otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return Turno.class.hashCode();
	}

}
