package com.barberya.barberia;

import com.barberya.auth.Usuario;

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
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "miembros_barberia")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MiembroBarberia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "barberia_id", nullable = false)
	private Barberia barberia;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RolMiembro rol;

	@Column(name = "atiende_turnos", nullable = false)
	private boolean atiendeTurnos = true;

	@Size(max = 80)
	@Column(name = "nombre_visible", length = 80)
	private String nombreVisible;

	@Column(name = "foto_url")
	private String fotoUrl;

	@Column(nullable = false)
	private boolean activo = true;

	public MiembroBarberia(Barberia barberia, Usuario usuario, RolMiembro rol) {
		this.barberia = barberia;
		this.usuario = usuario;
		this.rol = rol;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof MiembroBarberia otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return MiembroBarberia.class.hashCode();
	}

}
