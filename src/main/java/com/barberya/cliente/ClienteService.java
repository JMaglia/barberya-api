package com.barberya.cliente;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barberya.barberia.Barberia;
import com.barberya.cliente.dto.ClienteBusquedaResponse;
import com.barberya.error.DatosInvalidosException;
import com.barberya.error.RecursoNoEncontradoException;

/** Fichas de clientes de la barbería del token. El {@code barberiaId} llega siempre resuelto desde el token. */
@Service
public class ClienteService {

	static final int MINIMO_BUSQUEDA = 2;
	static final int MAXIMO_RESULTADOS = 10;

	private static final Pattern MARCAS = Pattern.compile("\\p{M}");
	/** Lo que alguien escribe al buscar un teléfono: dígitos, espacios, +, guiones y paréntesis. */
	private static final Pattern PARECE_TELEFONO = Pattern.compile("[0-9 +()-]*[0-9][0-9 +()-]*");

	private final ClienteRepository clientes;

	public ClienteService(ClienteRepository clientes) {
		this.clientes = clientes;
	}

	/**
	 * Fichas activas cuyo nombre contenga {@code q} (sin distinguir mayúsculas ni tildes) o, si {@code q} parece un
	 * teléfono, cuyo teléfono contenga esos dígitos sin importar el formato.
	 */
	@Transactional(readOnly = true)
	public List<ClienteBusquedaResponse> buscar(Long barberiaId, String q) {
		String texto = q == null ? "" : q.strip();
		if (texto.length() < MINIMO_BUSQUEDA) {
			throw new DatosInvalidosException("q", "Escribí al menos " + MINIMO_BUSQUEDA + " caracteres para buscar");
		}
		String digitos = PARECE_TELEFONO.matcher(texto).matches() ? texto.replaceAll("[^0-9]", "") : "";
		return clientes.buscar(barberiaId, contiene(normalizar(texto)), digitos.isEmpty() ? "" : contiene(digitos),
				MAXIMO_RESULTADOS).stream()
				.map(ClienteBusquedaResponse::de)
				.toList();
	}

	/** Ficha cargada a mano por el barbero: sin usuario. Los vacíos quedan null y el mail en minúsculas. */
	@Transactional
	public Cliente crearManual(Barberia barberia, String nombre, String telefono, String email) {
		Cliente ficha = new Cliente(barberia, nombre.strip());
		ficha.setTelefono(textoOpcional(telefono));
		String mail = textoOpcional(email);
		ficha.setEmail(mail == null ? null : mail.toLowerCase(Locale.ROOT));
		return clientes.saveAndFlush(ficha);
	}

	/** La ficha de la barbería (404 si no existe o es de otra), reactivada si estaba dada de baja. */
	@Transactional
	public Cliente buscarYReactivar(Long barberiaId, Long clienteId) {
		Cliente ficha = clientes.findByIdAndBarberiaId(clienteId, barberiaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese cliente"));
		ficha.setActivo(true);
		return ficha;
	}

	/** Minúsculas y sin tildes; tiene que coincidir con el {@code translate} de {@link ClienteRepository#buscar}. */
	static String normalizar(String texto) {
		return MARCAS.matcher(Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
	}

	/** Patrón LIKE "contiene", con los comodines del texto escapados. */
	private static String contiene(String texto) {
		return "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

	private static String textoOpcional(String valor) {
		return valor == null || valor.isBlank() ? null : valor.strip();
	}

}
