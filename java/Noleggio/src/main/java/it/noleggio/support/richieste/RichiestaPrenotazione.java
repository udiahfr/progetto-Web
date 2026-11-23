package it.noleggio.support.richieste;

import jakarta.validation.constraints.*;

import java.util.Map;
import java.time.LocalDateTime;

// Cosa può mandare il client per prenotare: niente id, stato, prezzi o cliente (li decide il server)
public record RichiestaPrenotazione(
        @NotNull LocalDateTime inizio,
        @NotNull LocalDateTime fine,
        // id del tipo di mezzo → quantità, es. { "2": 2, "1": 1 }
        @NotEmpty Map<@NotNull Long, @NotNull @Positive Integer> mezzi
) {}
