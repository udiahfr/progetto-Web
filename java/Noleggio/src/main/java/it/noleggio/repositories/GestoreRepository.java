package it.noleggio.repositories;

import it.noleggio.entities.Gestore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GestoreRepository extends JpaRepository<Gestore, Long> {
    // Operazione di ricerca di un utente tramite email
    // Hibernate si occupa di creare la query che limita la ricerca agli utenti WHERE ruolo = 'GESTORE'
    Optional<Gestore> findByEmail(String email);
}
