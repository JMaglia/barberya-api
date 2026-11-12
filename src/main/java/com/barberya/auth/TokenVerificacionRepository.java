package com.barberya.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface TokenVerificacionRepository extends JpaRepository<TokenVerificacion, Long> {

	/** Con bloqueo: el mismo token no se puede usar dos veces en paralelo. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TokenVerificacion> findFirstByTokenHashAndTipoAndUsadoEnIsNull(String tokenHash, TipoTokenVerificacion tipo);

}
