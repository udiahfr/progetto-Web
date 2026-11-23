package it.noleggio.services;

import it.noleggio.entities.TipoMezzo;
import it.noleggio.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;

@Service
public class PrezzoService {
    // Niente @Transactional: come DisponibilitaService, sono metodi di supporto utilizzati dentro la transazione di chi lo chiama
    @Autowired
    private FestivoRepository festivoRepository;

    // Prezzo di UN tipo di mezzo per la durata di tutto il noleggio: ore iniziate x prezzo orario
    // Per ogni giornata non si può pagare più del giornaliero
    public BigDecimal prezzoUnitario(TipoMezzo tipo, LocalDateTime inizio, LocalDateTime fine) {
        // Feriale o festivo dipende dal giorno del ritiro
        boolean festivo = festivo(inizio.toLocalDate());
        BigDecimal orario = festivo ? tipo.getPrezzoOrarioFestivo() : tipo.getPrezzoOrarioFeriale();
        BigDecimal giornaliero = festivo ? tipo.getPrezzoGiornalieroFestivo() : tipo.getPrezzoGiornalieroFeriale();

        long minuti = Duration.between(inizio, fine).toMinutes();
        long giorni = minuti / (24 * 60);          // giornate intere (blocchi di 24 ore dal ritiro)
        long minutiResto = minuti % (24 * 60);
        long oreResto = minutiResto / 60;
        if (minutiResto % 60 != 0) oreResto++;     // ora iniziata = ora pagata

        BigDecimal giornataIntera = orario.multiply(BigDecimal.valueOf(24)).min(giornaliero);
        BigDecimal resto = orario.multiply(BigDecimal.valueOf(oreResto)).min(giornaliero);
        return giornataIntera.multiply(BigDecimal.valueOf(giorni)).add(resto);
    }

    // Sabato, domenica o data nella tabella festivo
    public boolean festivo(LocalDate data) {
        DayOfWeek giorno = data.getDayOfWeek();
        return giorno == DayOfWeek.SATURDAY || giorno == DayOfWeek.SUNDAY || festivoRepository.existsByData(data);
    }
}
