package it.noleggio.support.exceptions;

public class LimitePrenotazioniSuperatoException extends Exception {
    public LimitePrenotazioniSuperatoException(String messaggio) {
        super(messaggio);
    }
}