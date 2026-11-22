package it.noleggio.support.exceptions;

public class UtenteNonTrovatoException extends Exception {
    public UtenteNonTrovatoException(String email) {
        super("Nessun utente registrato con l'email " + email);
    }
}