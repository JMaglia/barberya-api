package com.barberya.auth;

import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tokens_verificacion")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenVerificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** NULL si todavía no hay cuenta (login por código). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

	@NotBlank
	@Email
	@Size(max = 255)
	@Column(nullable = false)
	private String email;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoTokenVerificacion tipo;

	@NotBlank
	@Size(max = 255)
	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@NotNull
	@PositiveOrZero
	@Column(nullable = false)
	private Short intentos = 0;

	@NotNull
	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	@Column(name = "usado_en")
	private Instant usadoEn;

	@Generated(event = EventType.INSERT)
	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private Instant creadoEn;

	public TokenVerificacion(String email, TipoTokenVerificacion tipo, String tokenHash, Instant expiraEn) {
		this.email = email;
		this.tipo = tipo;
		this.tokenHash = tokenHash;
		this.expiraEn = expiraEn;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof TokenVerificacion otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return TokenVerificacion.class.hashCode();
	}

}
