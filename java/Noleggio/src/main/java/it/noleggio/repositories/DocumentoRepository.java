package it.noleggio.repositories;

import it.noleggio.entities.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    // Documento data una determinata prenotazione
    Optional<Documento> findByPrenotazione(Prenotazione prenotazione);
}
