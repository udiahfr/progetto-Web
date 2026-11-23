package it.noleggio.support.exceptions;

public class NegozioNonTrovatoException extends Exception {
    public NegozioNonTrovatoException(Long id) {
        super("Nessun negozio con id " + id);
    }
}
