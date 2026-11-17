package com.barberya.auth;

/** Verifica el ID token que el front obtiene con Google Identity Services. En los tests se reemplaza por uno falso. */
public interface VerificadorGoogle {

	/** @throws GoogleInvalidoException si el token no es válido, venció o no es para esta app */
	DatosGoogle verificar(String idToken);

}
