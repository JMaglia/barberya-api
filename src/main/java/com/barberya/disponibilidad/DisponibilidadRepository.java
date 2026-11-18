package com.barberya.disponibilidad;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.barberya.turno.EstadoTurno;
import com.barberya.turno.Turno;

/** Lo que ocupa la agenda de varios miembros en un rango, en una consulta por tipo (no una por día). */
interface DisponibilidadRepository extends Repository<Turno, Long> {

	/** Turnos en esos estados que se superponen con [desde, hasta). */
	@Query("""
			select new com.barberya.disponibilidad.OcupacionMiembro(t.miembro.id, t.inicio, t.fin)
			from Turno t
			where t.barberia.id = :barberiaId and t.miembro.id in :miembroIds and t.estado in :estados
			  and t.inicio < :hasta and t.fin > :desde
			""")
	List<OcupacionMiembro> turnosQueOcupan(@Param("barberiaId") Long barberiaId,
			@Param("miembroIds") Collection<Long> miembroIds, @Param("estados") Collection<EstadoTurno> estados,
			@Param("desde") Instant desde, @Param("hasta") Instant hasta);

	/** Bloqueos que se superponen con [desde, hasta). */
	@Query("""
			select new com.barberya.disponibilidad.OcupacionMiembro(b.miembro.id, b.inicio, b.fin)
			from BloqueoAgenda b
			where b.miembro.id in :miembroIds and b.inicio < :hasta and b.fin > :desde
			""")
	List<OcupacionMiembro> bloqueosEnRango(@Param("miembroIds") Collection<Long> miembroIds,
			@Param("desde") Instant desde, @Param("hasta") Instant hasta);

}
