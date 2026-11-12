package com.barberya.auth;

import java.time.Instant;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@NotBlank
	@Size(max = 255)
	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@NotNull
	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	@Column(nullable = false)
	private boolean revocado = false;

	/** Token que lo reemplazó al rotarlo. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reemplazado_por")
	private RefreshToken reemplazadoPor;

	/** Cookie de 30 días (true) o de sesión (false). Se copia al rotar. */
	@Column(name = "mantener_sesion", nullable = false)
	private boolean mantenerSesion = false;

	@Generated(event = EventType.INSERT)
	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private Instant creadoEn;

	public RefreshToken(Usuario usuario, String tokenHash, Instant expiraEn, boolean mantenerSesion) {
		this.usuario = usuario;
		this.tokenHash = tokenHash;
		this.expiraEn = expiraEn;
		this.mantenerSesion = mantenerSesion;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof RefreshToken otro && id != null && id.equals(otro.getId());
	}

	@Override
	public int hashCode() {
		return RefreshToken.class.hashCode();
	}

}
