package it.noleggio.repositories;

import it.noleggio.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
public interface FestivoRepository extends JpaRepository<Festivo, Long> {
    // Verifica se il giorno indicato è festivo
    boolean existsByData(LocalDate data);
}
