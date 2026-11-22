package it.noleggio.repositories;

import it.noleggio.entities.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    // Tutti i pagamenti di una prenotazione: sono al massimo due (ritiro e riconsegna),
    // quindi una List e non un Optional, che fallirebbe con due risultati
    List<Pagamento> findByPrenotazione(Prenotazione prenotazione);

    // Il pagamento di una prenotazione per una causale precisa (al massimo uno, UNIQUE nel DB)
    Optional<Pagamento> findByPrenotazioneAndCausale(Prenotazione prenotazione, CausalePagamento causale);
}
