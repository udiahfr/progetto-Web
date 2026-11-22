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

    @Basic
    private LocalDateTime riconsegnaEffettiva;

    @Basic
    private LocalDateTime ritardoEffettivo; // istante in cui arriva (in ritardo)

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

    // Manteniamo la lista dei mezzi prenotati all'interno della prenotazione, in modo da non dover rieffettuare una query ogni volta che ne abbiamo bisogno
    // Facendo un'operazione su MezziInPrenotazione verrà automaticamente aggiornata la lista di elementi all'interno di Prenotazione
    @OneToMany(mappedBy = "prenotazione", cascade = CascadeType.PERSIST)
    private List<MezziInPrenotazione> mezzi = new ArrayList<>();
}
