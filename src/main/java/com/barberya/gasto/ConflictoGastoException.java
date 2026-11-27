package com.barberya.gasto;

/** 409 del módulo de gastos (categoría repetida, categoría con gastos). */
public class ConflictoGastoException extends RuntimeException {

	private final String titulo;

	public ConflictoGastoException(String titulo, String mensaje) {
		super(mensaje);
		this.titulo = titulo;
	}

	public String getTitulo() {
		return titulo;
	}

	static ConflictoGastoException categoriaRepetida() {
		return new ConflictoGastoException("Categoría repetida", "Ya tenés una categoría con ese nombre");
	}

	static ConflictoGastoException categoriaConGastos() {
		return new ConflictoGastoException("Categoría en uso", "Esa categoría tiene gastos cargados y no se puede borrar");
	}

}
