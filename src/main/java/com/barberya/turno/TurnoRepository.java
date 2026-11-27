package com.barberya.turno;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	/** Mis turnos próximos: CONFIRMADO desde ahora, de todas las fichas del usuario. Con barbería y servicio. */
	@Query(value = """
			SELECT t FROM Turno t JOIN FETCH t.barberia JOIN FETCH t.servicio
			WHERE t.cliente.usuario.id = :usuarioId
			  AND t.estado = com.barberya.turno.EstadoTurno.CONFIRMADO AND t.inicio >= :ahora
			ORDER BY t.inicio ASC, t.id ASC
			""", countQuery = """
			SELECT count(t) FROM Turno t
			WHERE t.cliente.usuario.id = :usuarioId
			  AND t.estado = com.barberya.turno.EstadoTurno.CONFIRMADO AND t.inicio >= :ahora
			""")
	Page<Turno> findProximosDeUsuario(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora,
			Pageable pageable);

	/** Mis turnos pasados: el complemento de los próximos (ya empezaron, o no están CONFIRMADO). */
	@Query(value = """
			SELECT t FROM Turno t JOIN FETCH t.barberia JOIN FETCH t.servicio
			WHERE t.cliente.usuario.id = :usuarioId
			  AND (t.inicio < :ahora OR t.estado <> com.barberya.turno.EstadoTurno.CONFIRMADO)
			ORDER BY t.inicio DESC, t.id DESC
			""", countQuery = """
			SELECT count(t) FROM Turno t
			WHERE t.cliente.usuario.id = :usuarioId
			  AND (t.inicio < :ahora OR t.estado <> com.barberya.turno.EstadoTurno.CONFIRMADO)
			""")
	Page<Turno> findPasadosDeUsuario(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora,
			Pageable pageable);

	/** Turno cuya ficha de cliente es de ese usuario, con barbería y servicio. */
	@Query("""
			SELECT t FROM Turno t JOIN FETCH t.barberia JOIN FETCH t.servicio
			WHERE t.id = :id AND t.cliente.usuario.id = :usuarioId
			""")
	Optional<Turno> findDeUsuarioConDetalle(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

	/** Turno de esa barbería, bloqueado hasta el fin de la transacción. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM Turno t WHERE t.id = :id AND t.barberia.id = :barberiaId")
	Optional<Turno> findParaModificarDeBarberia(@Param("id") Long id, @Param("barberiaId") Long barberiaId);

	/**
	 * Agenda de un miembro: turnos que empiezan en [desde, hasta), con servicio y cliente (sin N+1). Los cancelados
	 * solo si se piden.
	 */
	@Query("""
			SELECT t FROM Turno t JOIN FETCH t.servicio JOIN FETCH t.cliente
			WHERE t.barberia.id = :barberiaId AND t.miembro.id = :miembroId
			  AND t.inicio >= :desde AND t.inicio < :hasta
			  AND (:incluirCancelados = true OR t.estado <> com.barberya.turno.EstadoTurno.CANCELADO)
			ORDER BY t.inicio ASC, t.id ASC
			""")
	List<Turno> buscarAgenda(@Param("barberiaId") Long barberiaId, @Param("miembroId") Long miembroId,
			@Param("desde") Instant desde, @Param("hasta") Instant hasta,
			@Param("incluirCancelados") boolean incluirCancelados);

	/**
	 * Si otro turno del miembro que ocupa horario (no CANCELADO ni AUSENTE, igual que turnos_sin_superposicion) se pisa
	 * con [inicio, fin). Los contiguos no cuentan.
	 */
	@Query("""
			SELECT count(t) > 0 FROM Turno t
			WHERE t.miembro.id = :miembroId AND t.id <> :excluirId
			  AND t.estado NOT IN (com.barberya.turno.EstadoTurno.CANCELADO, com.barberya.turno.EstadoTurno.AUSENTE)
			  AND t.inicio < :fin AND t.fin > :inicio
			""")
	boolean existeSuperpuesto(@Param("miembroId") Long miembroId, @Param("inicio") Instant inicio,
			@Param("fin") Instant fin, @Param("excluirId") Long excluirId);

}
