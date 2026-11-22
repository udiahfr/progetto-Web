package it.noleggio.support.exceptions;

public class UtenteGiaRegistratoException extends Exception {
    public UtenteGiaRegistratoException(String email) {
        super("Esiste già un utente registrato con l'email " + email);
    }
}