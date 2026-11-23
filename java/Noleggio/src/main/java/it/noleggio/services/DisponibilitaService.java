package it.noleggio.services;

import it.noleggio.entities.*;
import it.noleggio.repositories.*;
import it.noleggio.support.exceptions.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

@Service
public class DisponibilitaService {
    // Non sono metodi transazionali:
    // sono metodi di supporto richiamati all'interno del metodo transazionale di creazione della prenotazione

    // @Value recupera la variabile definita nel application.yaml
    @Value("${noleggio.tolleranza-ritiro-minuti}")
    private long tolleranzaRitiroMinuti;

    @Autowired
    private MezziInNegozioRepository mezziInNegozioRepository;
    @Autowired
    private MezziInPrenotazioneRepository mezziInPrenotazioneRepository;
    @Autowired
    private OrarioRepository orarioRepository;

    // Avendo usato il tipo LocalDateTime usiamo i metodi della classe per effettuare controlli sulla validità dell'intervallo
    // Intervallo non valido: ritiro dopo riconsegna, nel passato, non ogni 30 minuti
    public void controllaIntervallo(LocalDateTime inizio, LocalDateTime fine) throws IntervalloNonValidoException {
        if (!inizio.isBefore(fine))
            throw new IntervalloNonValidoException("La riconsegna (" + fine + ") deve essere dopo il ritiro (" + inizio + ")");
        if (inizio.isBefore(LocalDateTime.now()))
            throw new IntervalloNonValidoException("Il ritiro (" + inizio + ") è nel passato");
        if (!aStepDiMezzOra(inizio) || !aStepDiMezzOra(fine))
            throw new IntervalloNonValidoException("Ritiro e riconsegna devono essere a step di 30 minuti (es. 9:00, 9:30)");
    }
    // metodo utilizzato in controllaIntervallo
    private boolean aStepDiMezzOra(LocalDateTime t) {
        return t.getMinute() % 30 == 0 && t.getSecond() == 0 && t.getNano() == 0;
    }

    // Controllo se il negozio è aperto al momento della riconsegna del veicolo e al ritito
    public void controllaApertura(Negozio negozio, LocalDateTime inizio, LocalDateTime fine) throws NegozioChiusoException {
        if (!aperto(negozio, inizio)) throw new NegozioChiusoException(negozio, "il ritiro", inizio);
        if (!aperto(negozio, fine)) throw new NegozioChiusoException(negozio, "la riconsegna", fine);
    }

    // restituisce True se il negozio è aperto al momento
    private boolean aperto(Negozio negozio, LocalDateTime momento) {
        Optional<Orario> orario = orarioRepository.findByNegozioAndGiorno(negozio, momento.getDayOfWeek());
        if (orario.isEmpty()) return false;   // nessuna riga per quel giorno = chiuso
        LocalTime ora = momento.toLocalTime();
        return !ora.isBefore(orario.get().getApertura()) && !ora.isAfter(orario.get().getChiusura());
    }

    // Verifica se tipo mezzo è disponibile: mezzi in servizio - mezzi impegnati nell'intervallo
    public long mezziLiberi(Negozio negozio, TipoMezzo tipo, LocalDateTime inizio, LocalDateTime fine) {
        Optional<MezziInNegozio> inNegozio = mezziInNegozioRepository.findByNegozioAndTipoMezzo(negozio, tipo);
        long totali = inNegozio.isEmpty() ? 0 : inNegozio.get().getQuantita();   // nessuna riga = tipo assente
        LocalDateTime limiteScadenza = limiteScadenza();
        Long somma = mezziInPrenotazioneRepository.contaMezziImpegnati(negozio, tipo, inizio, fine, limiteScadenza);
        long impegnati = (somma == null) ? 0 : somma;   // SUM senza righe restituisce null
        return totali - impegnati;
    }

    // Verifica mezziLiberi per tutti i tipi di mezzi richiesti
    public void controllaDisponibilita(Negozio negozio, Map<TipoMezzo, Integer> richiesta, LocalDateTime inizio, LocalDateTime fine) throws DisponibilitaInsufficienteException {
        for (Map.Entry<TipoMezzo, Integer> voce : richiesta.entrySet()) {
            long liberi = mezziLiberi(negozio, voce.getKey(), inizio, fine);
            if (liberi < voce.getValue())
                throw new DisponibilitaInsufficienteException(negozio, voce.getKey(), liberi, voce.getValue());
        }
    }

    // Il metodo contaMezziImpegnati calcola il numero di mezzi occupati nello specifico intervallo di tempo: nel caso in cui una prenotazione sia scaduta
    // (il cliente non ha ritirato i mezzi entro i 30 minuti dall'orario di ritiro) i mezzi tornano ad essere liberi
    // -> inizio + tolleranza >= adesso: prenotazione ancora valida
    // -> inizio + tolleranza < adesso: prenotazione non valida (mezzi liberi)
    // Quindi, il limite scadenza rappresenta l'intervallo di tutti i mezzi che devono essere considerati liberi rispetto all'orario della corrente prenotazione calcolato come
    // limiteScadenza = adesso - 30min (tolleranza del ritiro)
    public LocalDateTime limiteScadenza() {
        return LocalDateTime.now().minusMinutes(tolleranzaRitiroMinuti);
    }
}
