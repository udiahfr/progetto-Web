package it.noleggio.support.exceptions;

public class TipoMezzoNonTrovatoException extends Exception {
    public TipoMezzoNonTrovatoException(Long id) {
        super("Nessun tipo di mezzo con id " + id);
    }
}