package com.barberya.turno;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

	Optional<Turno> findByIdAndBarberiaId(Long id, Long barberiaId);

	/** Turnos del miembro en ese estado que se pisan con [desde, hasta). Los contiguos no cuentan. */
	long countByMiembroIdAndEstadoAndInicioLessThanAndFinGreaterThan(Long miembroId, EstadoTurno estado, Instant hasta,
			Instant desde);

	/** Para el tope de turnos futuros por ficha de cliente. */
	long countByClienteIdAndEstadoAndInicioGreaterThan(Long clienteId, EstadoTurno estado, Instant ahora);

	/** Turno cuya ficha de cliente es de ese usuario, bloqueado hasta el fin de la transacción. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM Turno t WHERE t.id = :id AND t.cliente.usuario.id = :usuarioId")
	Optional<Turno> findParaCancelarDeUsuario(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

	/** Turno de esa barbería, bloqueado hasta el fin de la transacción. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM Turno t WHERE t.id = :id AND t.barberia.id = :barberiaId")
	Optional<Turno> findParaCancelarDeBarberia(@Param("id") Long id, @Param("barberiaId") Long barberiaId);

}
