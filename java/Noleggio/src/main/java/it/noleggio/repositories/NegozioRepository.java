package it.noleggio.repositories;

import it.noleggio.entities.Negozio;
import jakarta.persistence.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface NegozioRepository extends JpaRepository<Negozio, Long> {
    // Operazione di ricerca per aggiornamento: restituisce il negozio con id specificato, con lock per aggiornamento
    // Chi arriva secondo aspetta il commit del primo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Negozio n WHERE n.id = :id")
    Optional<Negozio> findByIdPerAggiornamento(@Param("id") Long id);

    // Operazione di ricerca sulla mappa: restituisce la lista di negozi dentro un "quadrato" attorno alla posizione del cliente che cerca;
    // il filtro preciso sul raggio e l'ordinamento per distanza si fanno nel service
    List<Negozio> findByLatitudineBetweenAndLongitudineBetween(double latMin, double latMax, double lonMin, double lonMax);
}
