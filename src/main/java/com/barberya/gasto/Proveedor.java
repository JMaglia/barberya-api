package com.barberya.gasto;

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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Baja lógica: un proveedor inactivo no se ofrece al cargar gastos nuevos. */
@Entity
@Table(name = "proveedores")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proveedor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "barberia_id", nullable = false)
	private Barberia barberia;

	@NotBlank
	@Size(max = 120)
	@Column(nullable = false, length = 120)
	private String nombre;

	@Size(max = 80)
	@Column(length = 80)
	private String rubro;

	@Size(max = 30)
	@Column(length = 30)
	private String telefono;

	@Email
	@Size(max = 255)
	private String email;

	private String notas;

	@Column(nullable = false)
	private boolean activo = true;

	public Proveedor(Barberia barberia, String nombre) {
		this.barberia = barberia;
		this.nombre = nombre;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Proveedor otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return Proveedor.class.hashCode();
	}

}
