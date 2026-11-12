package com.barberya.agenda;

import java.time.LocalTime;

import com.barberya.barberia.MiembroBarberia;

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
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Franja semanal de atención. Dos filas el mismo día = turno partido. */
@Entity
@Table(name = "horarios_atencion")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HorarioAtencion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "miembro_id", nullable = false)
	private MiembroBarberia miembro;

	/** 0 = domingo. */
	@NotNull
	@Min(0)
	@Max(6)
	@Column(name = "dia_semana", nullable = false)
	private Short diaSemana;

	@NotNull
	@Column(name = "hora_inicio", nullable = false)
	private LocalTime horaInicio;

	@NotNull
	@Column(name = "hora_fin", nullable = false)
	private LocalTime horaFin;

	public HorarioAtencion(MiembroBarberia miembro, Short diaSemana, LocalTime horaInicio, LocalTime horaFin) {
		this.miembro = miembro;
		this.diaSemana = diaSemana;
		this.horaInicio = horaInicio;
		this.horaFin = horaFin;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof HorarioAtencion otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return HorarioAtencion.class.hashCode();
	}

}
