package com.barberya.auth;

import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** En minúsculas: la base lo exige con un CHECK; el servicio lo normaliza antes de guardar. */
	@NotBlank
	@Email
	@Size(max = 255)
	@Column(nullable = false)
	private String email;

	/** BCrypt. NULL si entra con Google o con código. */
	@Size(max = 255)
	@Column(name = "password_hash")
	private String passwordHash;

	@Size(max = 255)
	@Column(name = "google_sub")
	private String googleSub;

	@Column(name = "email_verificado", nullable = false)
	private boolean emailVerificado = false;

	@Column(name = "es_admin", nullable = false)
	private boolean esAdmin = false;

	@NotBlank
	@Size(max = 80)
	@Column(nullable = false, length = 80)
	private String nombre;

	@Size(max = 80)
	@Column(length = 80)
	private String apellido;

	@Size(max = 30)
	@Column(length = 30)
	private String telefono;

	@Generated(event = EventType.INSERT)
	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private Instant creadoEn;

	public Usuario(String email, String nombre) {
		this.email = email;
		this.nombre = nombre;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Usuario otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return Usuario.class.hashCode();
	}

}
