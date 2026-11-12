package com.barberya.agenda;

import java.time.Instant;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bloqueos_agenda")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BloqueoAgenda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "miembro_id", nullable = false)
	private MiembroBarberia miembro;

	@NotNull
	@Column(nullable = false)
	private Instant inicio;

	@NotNull
	@Column(nullable = false)
	private Instant fin;

	@Size(max = 120)
	@Column(length = 120)
	private String motivo;

	public BloqueoAgenda(MiembroBarberia miembro, Instant inicio, Instant fin) {
		this.miembro = miembro;
		this.inicio = inicio;
		this.fin = fin;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof BloqueoAgenda otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return BloqueoAgenda.class.hashCode();
	}

}
