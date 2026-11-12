package com.barberya.barberia;

import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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
@Table(name = "barberias")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Barberia {

	public static final String ZONA_HORARIA_POR_DEFECTO = "America/Argentina/Buenos_Aires";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 120)
	@Column(nullable = false, length = 120)
	private String nombre;

	@NotBlank
	@Size(max = 60)
	@Column(nullable = false, length = 60)
	private String slug;

	private String descripcion;

	@Size(max = 200)
	@Column(length = 200)
	private String direccion;

	@Size(max = 30)
	@Column(length = 30)
	private String telefono;

	@Size(max = 60)
	@Column(length = 60)
	private String instagram;

	@Column(name = "logo_url")
	private String logoUrl;

	@Column(name = "foto_portada_url")
	private String fotoPortadaUrl;

	@NotBlank
	@Size(max = 50)
	@Column(name = "zona_horaria", nullable = false, length = 50)
	private String zonaHoraria = ZONA_HORARIA_POR_DEFECTO;

	@NotNull
	@PositiveOrZero
	@Column(name = "cancelar_hasta_horas", nullable = false)
	private Integer cancelarHastaHoras = 24;

	@NotNull
	@Positive
	@Column(name = "reserva_max_dias", nullable = false)
	private Integer reservaMaxDias = 30;

	@NotNull
	@PositiveOrZero
	@Column(name = "pausa_entre_turnos_min", nullable = false)
	private Integer pausaEntreTurnosMin = 0;

	@Column(nullable = false)
	private boolean activa = true;

	@Generated(event = EventType.INSERT)
	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private Instant creadoEn;

	public Barberia(String nombre, String slug) {
		this.nombre = nombre;
		this.slug = slug;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Barberia otra && id != null && id.equals(otra.getId());
	}

	@Override
	public int hashCode() {
		return Barberia.class.hashCode();
	}

}
