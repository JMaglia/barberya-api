package com.barberya.auth.dto;

public record TokenResponse(String accessToken, String tipo, long expiraEnSegundos) {

	public TokenResponse(String accessToken, long expiraEnSegundos) {
		this(accessToken, "Bearer", expiraEnSegundos);
	}

	@Override
	public String toString() {
		return "TokenResponse[tipo=" + tipo + ", expiraEnSegundos=" + expiraEnSegundos + "]";
	}

}
