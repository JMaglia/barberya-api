package com.barberya.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TokenVerificacionRepository extends JpaRepository<TokenVerificacion, Long> {

	/** Con bloqueo: el mismo token no se puede usar dos veces en paralelo. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TokenVerificacion> findFirstByTokenHashAndTipoAndUsadoEnIsNull(String tokenHash, TipoTokenVerificacion tipo);

	/**
	 * El último código sin usar del mail. Con bloqueo: los intentos simultáneos sobre el mismo código esperan su turno,
	 * así ninguno se pierde de la cuenta de {@code intentos}.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TokenVerificacion> findFirstByEmailAndTipoAndUsadoEnIsNullOrderByIdDesc(String email, TipoTokenVerificacion tipo);

	/** Vence los códigos vigentes del mail. Toca {@code expira_en} y no {@code usado_en}, que queda para "se usó". */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			UPDATE TokenVerificacion t SET t.expiraEn = :ahora
			WHERE t.email = :email AND t.tipo = :tipo AND t.usadoEn IS NULL AND t.expiraEn > :ahora
			""")
	int vencerVigentes(@Param("email") String email, @Param("tipo") TipoTokenVerificacion tipo,
			@Param("ahora") Instant ahora);

}
