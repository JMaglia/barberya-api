package com.barberya.comun.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** Una página de un listado. {@code pagina} empieza en 0. */
public record PaginaResponse<T>(
		List<T> contenido,
		int pagina,
		int tamanio,
		long totalElementos,
		int totalPaginas) {

	public static <T> PaginaResponse<T> de(Page<T> pagina) {
		return new PaginaResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
				pagina.getTotalElements(), pagina.getTotalPages());
	}

}
