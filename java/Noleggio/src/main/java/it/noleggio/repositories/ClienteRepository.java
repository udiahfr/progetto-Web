package it.noleggio.repositories;

import it.noleggio.entities.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    // Operazione di ricerca di un utente tramite email
    // Hibernate si occupa di creare la query che limita la ricerca agli utenti WHERE ruolo = 'CLIENTE'
    Optional<Cliente> findByEmail(String email);
}
