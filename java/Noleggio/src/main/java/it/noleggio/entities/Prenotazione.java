package it.noleggio.entities;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Getter
@Setter
public class Prenotazione {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Basic
    @Column(nullable = false)
    private LocalDateTime inizio;

    @Basic
    @Column(nullable = false)
    private LocalDateTime fine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatoPrenotazione stato;

    @Basic
    @Enumerated(EnumType.STRING)
    private MotivoAnnullamento motivoAnnullamento;

    // Ora effettiva di ritiro: registrata sempre al ritiro (T1), anche se il cliente è puntuale.
    // Il ritardo al ritiro si calcola confrontandola con inizio.
    @Basic
    private LocalDateTime ritiroEffettivo;

    // Ora effettiva di riconsegna (T2): confrontata con fine per la penale di ritardo.
    @Basic
    private LocalDateTime riconsegnaEffettiva;

    // Per ora non usato: prenotazione avvisata perché un cliente precedente è in ritardo (§5.9)
    // @Column(nullable = false)
    // private boolean coinvoltaRitardo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "negozio_id", nullable = false)
    private Negozio negozio;

    @OneToOne // Optional = true implicito
    @JoinColumn(name = "prenotazione_originale_id", unique = true) // creazione di una nuova prenotazione per cancellazione della vecchia
    @JsonIgnore
    private Prenotazione originale;

    // Lock ottimistico
    @Version
    @JsonIgnore
    private long version;

    // Righe della prenotazione (R8): quanti mezzi di ogni tipo e a che prezzo.
    // La FK sta in MezziInPrenotazione (mappedBy). cascade = PERSIST: salvando una
    // prenotazione nuova si salvano anche le sue righe, con un solo save.
    // Solo PERSIST: le righe non cambiano dopo la creazione e le prenotazioni
    // non si cancellano (si annullano), quindi niente REMOVE.
    @OneToMany(mappedBy = "prenotazione", cascade = CascadeType.PERSIST)
    private List<MezziInPrenotazione> mezzi = new ArrayList<>();
}
