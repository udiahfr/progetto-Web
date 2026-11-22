package it.noleggio.repositories;

import it.noleggio.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TipoMezzoRepository extends JpaRepository<TipoMezzo, Long> {
    // METODI EREDITATI sono sufficienti: si utilizzeranno findAll per il listino e il metodo findById
}
