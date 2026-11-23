package it.noleggio.support.exceptions;

import it.noleggio.entities.Negozio;

import java.time.LocalDateTime;

public class NegozioChiusoException extends Exception {
    public NegozioChiusoException(Negozio negozio, String operazione, LocalDateTime quando) {
        super("Il negozio " + negozio.getNome() + " è chiuso all'ora scelta per " + operazione + " (" + quando + ")");
    }
}