package com.barberya.cliente;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	Optional<Cliente> findByIdAndBarberiaId(Long id, Long barberiaId);

	/** La ficha de ese usuario en esa barbería, bloqueada: dos reservas del mismo cliente no se saltean el tope. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM Cliente c WHERE c.barberia.id = :barberiaId AND c.usuario.id = :usuarioId")
	Optional<Cliente> findParaReservar(@Param("barberiaId") Long barberiaId, @Param("usuarioId") Long usuarioId);

}
