package it.noleggio.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class MezziInNegozio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // La relazione 0:n - 0:n non diventa ManyToMany in quanto c'è un attributo della relazione -> diventano due ManyToOne
    @ManyToOne(optional = false)
    @JoinColumn(name = "negozio_id", nullable = false)
    @JsonIgnore
    private Negozio negozio;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tipo_mezzo_id", nullable = false)
    // il tipo di mezzo resta visibile per il gestore o cliente deve poter leggere
    private TipoMezzo tipoMezzo;

    @Basic
    @Column(nullable = false)
    private int quantita;
}
