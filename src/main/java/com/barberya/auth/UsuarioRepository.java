package com.barberya.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/** El mail tiene que venir ya en minúsculas. */
	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

}
