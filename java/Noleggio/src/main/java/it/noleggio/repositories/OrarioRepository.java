package it.noleggio.repositories;

import it.noleggio.entities.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.*;

@Repository
public interface OrarioRepository extends JpaRepository<Orario, Long> {
    // Operazione di verifica di apertura/chiusura di uno specifico negozio
    Optional<Orario> findByNegozioAndGiorno(Negozio negozio, DayOfWeek giorno);

    // Orari ordinati per giorno da mostrare al cliente quando prenota
    List<Orario> findByNegozioOrderByGiorno(Negozio negozio);
}
