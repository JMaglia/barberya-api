package com.barberya.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/** El mail tiene que venir ya en minúsculas. */
	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

	Optional<Usuario> findByGoogleSub(String googleSub);

	/** Bloquea la fila hasta el fin de la transacción: serializa operaciones del mismo usuario. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM Usuario u WHERE u.id = :id")
	Optional<Usuario> findByIdParaActualizar(@Param("id") Long id);

}
