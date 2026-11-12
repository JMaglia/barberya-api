package com.barberya;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.auth.Usuario;
import com.barberya.auth.UsuarioRepository;
import com.barberya.barberia.Barberia;
import com.barberya.barberia.BarberiaRepository;
import com.barberya.barberia.MiembroBarberia;
import com.barberya.barberia.MiembroBarberiaRepository;
import com.barberya.barberia.RolMiembro;
import com.barberya.cliente.Cliente;
import com.barberya.cliente.ClienteRepository;
import com.barberya.gasto.Proveedor;
import com.barberya.gasto.ProveedorRepository;
import com.barberya.servicio.Servicio;
import com.barberya.servicio.ServicioRepository;
import com.barberya.turno.EstadoTurno;
import com.barberya.turno.Turno;
import com.barberya.turno.TurnoRepository;

import jakarta.persistence.EntityManager;

/**
 * Guarda y relee entidades con los repositorios contra el Postgres real.
 * {@code @Transactional}: cada test se revierte al terminar.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RepositoriosJpaTest {

	private static final Instant LAS_10 = Instant.parse("2026-12-01T13:00:00Z");
	private static final Instant LAS_10_30 = Instant.parse("2026-12-01T13:30:00Z");
	private static final BigDecimal PRECIO = new BigDecimal("9900.00");

	@Autowired
	private EntityManager entityManager;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private BarberiaRepository barberias;
	@Autowired
	private MiembroBarberiaRepository miembros;
	@Autowired
	private ClienteRepository clientes;
	@Autowired
	private ServicioRepository servicios;
	@Autowired
	private TurnoRepository turnos;
	@Autowired
	private ProveedorRepository proveedores;

	@Test
	void guardaYReleeUnaBarberiaConSuMiembroClienteServicioYTurno() {
		Datos datos = crearBarberiaConTurno("uno");

		assertThat(datos.barberia().getCreadoEn()).isNotNull();
		assertThat(datos.cliente().getCreadoEn()).isNotNull();
		assertThat(datos.turno().getCreadoEn()).isNotNull();

		limpiarContexto();
		Long barberiaId = datos.barberia().getId();

		MiembroBarberia miembro = miembros.findByIdAndBarberiaId(datos.miembro().getId(), barberiaId).orElseThrow();
		assertThat(miembro.getRol()).isEqualTo(RolMiembro.DUENO);
		assertThat(miembro.isAtiendeTurnos()).isTrue();
		assertThat(miembro.getUsuario().getEmail()).isEqualTo("barbero-uno@mail.com");

		Cliente cliente = clientes.findByIdAndBarberiaId(datos.cliente().getId(), barberiaId).orElseThrow();
		assertThat(cliente.getNombre()).isEqualTo("Cliente uno");
		assertThat(cliente.isActivo()).isTrue();

		Servicio servicio = servicios.findByIdAndBarberiaId(datos.servicio().getId(), barberiaId).orElseThrow();
		assertThat(servicio.getDuracionMin()).isEqualTo(30);
		assertThat(servicio.getPrecio()).isEqualByComparingTo(PRECIO);

		Turno turno = turnos.findByIdAndBarberiaId(datos.turno().getId(), barberiaId).orElseThrow();
		assertThat(turno.getInicio()).isEqualTo(LAS_10);
		assertThat(turno.getFin()).isEqualTo(LAS_10_30);
		assertThat(turno.getPrecio()).isEqualByComparingTo(PRECIO);
		assertThat(turno.getEstado()).isEqualTo(EstadoTurno.CONFIRMADO);
		assertThat(turno.getCreadoEn()).isNotNull();
		assertThat(turno.getBarberia()).isEqualTo(miembro.getBarberia());
		assertThat(turno.getMiembro()).isEqualTo(miembro);
		assertThat(turno.getCliente()).isEqualTo(cliente);
		assertThat(turno.getServicio()).isEqualTo(servicio);

		Barberia barberia = barberias.findById(barberiaId).orElseThrow();
		assertThat(barberia.getZonaHoraria()).isEqualTo("America/Argentina/Buenos_Aires");
		assertThat(barberia.getCancelarHastaHoras()).isEqualTo(24);
	}

	@Test
	void findByIdAndBarberiaIdConOtraBarberiaDevuelveVacio() {
		Datos datos = crearBarberiaConTurno("uno");
		Long otraBarberiaId = crearBarberiaConTurno("dos").barberia().getId();
		limpiarContexto();

		assertThat(miembros.findByIdAndBarberiaId(datos.miembro().getId(), otraBarberiaId)).isEmpty();
		assertThat(clientes.findByIdAndBarberiaId(datos.cliente().getId(), otraBarberiaId)).isEmpty();
		assertThat(servicios.findByIdAndBarberiaId(datos.servicio().getId(), otraBarberiaId)).isEmpty();
		assertThat(turnos.findByIdAndBarberiaId(datos.turno().getId(), otraBarberiaId)).isEmpty();
	}

	@Test
	void findByBarberiaIdAndActivoTrueNoDevuelveProveedoresInactivosNiDeOtraBarberia() {
		Barberia barberia = barberias.save(new Barberia("Barberia uno", "barberia-uno"));
		Barberia otra = barberias.save(new Barberia("Barberia dos", "barberia-dos"));
		Proveedor navajin = proveedores.save(new Proveedor(barberia, "Navajin"));
		Proveedor peines = proveedores.save(new Proveedor(barberia, "Peines SA"));
		Proveedor inactivo = new Proveedor(barberia, "Viejo proveedor");
		inactivo.setActivo(false);
		proveedores.save(inactivo);
		proveedores.save(new Proveedor(otra, "De la otra"));
		limpiarContexto();

		List<Proveedor> activos = proveedores.findByBarberiaIdAndActivoTrue(barberia.getId());

		assertThat(activos).extracting(Proveedor::getId)
				.containsExactlyInAnyOrder(navajin.getId(), peines.getId());
	}

	private Datos crearBarberiaConTurno(String sufijo) {
		Usuario usuario = usuarios.save(new Usuario("barbero-" + sufijo + "@mail.com", "Barbero"));
		Barberia barberia = barberias.save(new Barberia("Barberia " + sufijo, "barberia-" + sufijo));
		MiembroBarberia miembro = miembros.save(new MiembroBarberia(barberia, usuario, RolMiembro.DUENO));
		Cliente cliente = clientes.save(new Cliente(barberia, "Cliente " + sufijo));
		Servicio servicio = servicios.save(new Servicio(barberia, "Corte", 30, PRECIO));
		Turno turno = turnos.save(new Turno(barberia, miembro, cliente, servicio, LAS_10, LAS_10_30, PRECIO));
		return new Datos(barberia, miembro, cliente, servicio, turno);
	}

	/** Manda los inserts a la base y vacía la caché, para que las lecturas vayan a Postgres. */
	private void limpiarContexto() {
		entityManager.flush();
		entityManager.clear();
	}

	private record Datos(Barberia barberia, MiembroBarberia miembro, Cliente cliente, Servicio servicio, Turno turno) {
	}

}
