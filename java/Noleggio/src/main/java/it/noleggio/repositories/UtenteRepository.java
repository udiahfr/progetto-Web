package it.noleggio.repositories;

import it.noleggio.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UtenteRepository extends JpaRepository<Utente, Long>{
    // Operazione di ricerca di un utente tramite email
    Optional<Utente> findByEmail(String email);

    // Verifica se esiste un utente con l'email specificata
    boolean existsByEmail(String email);
}
