package com.barberya.agenda;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** No hay barberia_id: se filtra siempre por el miembro o por la barbería del miembro. */
public interface BloqueoAgendaRepository extends JpaRepository<BloqueoAgenda, Long> {

	Optional<BloqueoAgenda> findByIdAndMiembroBarberiaId(Long id, Long barberiaId);

	/** Bloqueos que se superponen con [desde, hasta). */
	@Query("""
			select b from BloqueoAgenda b
			where b.miembro.id = :miembroId and b.inicio < :hasta and b.fin > :desde
			order by b.inicio, b.id
			""")
	List<BloqueoAgenda> buscarEnRango(@Param("miembroId") Long miembroId, @Param("desde") Instant desde,
			@Param("hasta") Instant hasta);

}
