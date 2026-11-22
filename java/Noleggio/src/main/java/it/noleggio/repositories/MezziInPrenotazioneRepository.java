package it.noleggio.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.noleggio.entities.*;

import java.time.LocalDateTime;

@Repository
public interface MezziInPrenotazioneRepository extends JpaRepository<MezziInPrenotazione, Long> {
    // NB: il funzionamento prevede una chiamata a questo metodo per tipo di mezzo prenotato
    // SPIEGAZIONE DELLA QUERY
    // Disponibilità: quanti mezzi di un tipo sono già impegnati nel negozio nell'intervallo [inizio, fine).
    // Contano solo le prenotazioni attive:
    // - PRENOTATA non scaduta (ritiro + tolleranza non ancora passati, §4.8);
    // - IN_CORSO, fino alla fine prevista.
    // Due intervalli si sovrappongono se inizioA < fineB e inizioB < fineA.
    // SUM restituisce null se non c'è niente da sommare: nel service null diventa 0.
    // Per ora non gestito: un noleggio IN_CORSO in ritardo dovrebbe occupare i mezzi
    // fino ad adesso + un margine (§7, nota sui ritardi) e la proroga dovrebbe poter
    // escludere la prenotazione stessa (§5.8).
    @Query("""
    SELECT SUM(m.quantita)
    FROM MezziInPrenotazione m JOIN m.prenotazione p
    WHERE p.negozio = :negozio
      AND m.tipoMezzo = :tipoMezzo
      AND p.inizio < :fine
      AND p.fine > :inizio
      AND (p.stato = it.noleggio.entities.StatoPrenotazione.IN_CORSO
           OR (p.stato = it.noleggio.entities.StatoPrenotazione.PRENOTATA
               AND p.inizio >= :limiteScadenza))
    """)
    Long contaMezziImpegnati(@Param("negozio") Negozio negozio,
                             @Param("tipoMezzo") TipoMezzo tipoMezzo,
                             @Param("inizio") LocalDateTime inizio,
                             @Param("fine") LocalDateTime fine,
                             @Param("limiteScadenza") LocalDateTime limiteScadenza);
}
