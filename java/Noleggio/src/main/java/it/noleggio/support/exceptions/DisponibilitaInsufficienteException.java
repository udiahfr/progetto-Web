package it.noleggio.support.exceptions;

import it.noleggio.entities.*;

public class DisponibilitaInsufficienteException extends Exception {
    public DisponibilitaInsufficienteException(Negozio negozio, TipoMezzo tipo, long liberi, int richiesti) {
        super("Nel negozio " + negozio.getNome() + " sono liberi " + liberi + " mezzi di tipo "
                + tipo.getNome() + " nell'intervallo scelto, ne servono " + richiesti);
    }
}