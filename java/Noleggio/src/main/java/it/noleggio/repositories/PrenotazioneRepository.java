package it.noleggio.repositories;

import it.noleggio.entities.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PrenotazioneRepository extends JpaRepository<Prenotazione, Long> {
    // Area del cliente: le sue prenotazioni, paginate
    Page<Prenotazione> findByCliente(Cliente cliente, Pageable pageable);

    // Verifica del limite di prenotazioni attive per cliente
    // IN_CORSO or PRENOTATA (non scaduta)
    @Query("""
        SELECT COUNT(p) FROM Prenotazione p
        WHERE p.cliente = :cliente
          AND (p.stato = it.noleggio.entities.StatoPrenotazione.IN_CORSO
               OR (p.stato = it.noleggio.entities.StatoPrenotazione.PRENOTATA
                   AND p.inizio >= :limiteScadenza))
          """)
    long contaPrenotazioniAttive(@Param("cliente") Cliente cliente,
                                 @Param("limiteScadenza") LocalDateTime limiteScadenza);

    // Area del gestore del negozio: solo il suo negozio, paginato
    // Se un parametro è null viene ignorato il suo filtro
    @Query("""
        SELECT p FROM Prenotazione p
        WHERE p.negozio = :negozio
          AND (:stato IS NULL OR p.stato = :stato)
          AND (:da IS NULL OR p.inizio >= :da)
          AND (:a  IS NULL OR p.inizio <  :a)
          AND (:testo IS NULL
               OR LOWER(p.cliente.nome)    LIKE LOWER(CONCAT('%', :testo, '%'))
               OR LOWER(p.cliente.cognome) LIKE LOWER(CONCAT('%', :testo, '%'))
               OR p.cliente.telefono       LIKE CONCAT('%', :testo, '%'))
        """)
    Page<Prenotazione> cercaPerGestore(@Param("negozio") Negozio negozio,
                                       @Param("stato") StatoPrenotazione stato,
                                       @Param("da") LocalDateTime da,
                                       @Param("a") LocalDateTime a,
                                       @Param("testo") String testo,
                                       Pageable pageable);
}
