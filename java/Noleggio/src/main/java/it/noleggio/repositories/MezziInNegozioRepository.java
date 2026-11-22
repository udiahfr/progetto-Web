package it.noleggio.repositories;

import it.noleggio.entities.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MezziInNegozioRepository extends JpaRepository<MezziInNegozio, Long> {
    // Quanti mezzi di quel tipo ha un negozio
    Optional<MezziInNegozio> findByNegozioAndTipoMezzo(Negozio negozio, TipoMezzo tipoMezzo);

    // Tutti i tipi con le quantità
    List<MezziInNegozio> findByNegozio(Negozio negozio);
}
