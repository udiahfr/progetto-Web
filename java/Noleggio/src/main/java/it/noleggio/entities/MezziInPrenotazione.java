package it.noleggio.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class MezziInPrenotazione {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "prenotazione_id", nullable = false)
    @JsonIgnore
    private Prenotazione prenotazione;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tipo_mezzo_id", nullable = false)
    private Tipo_Mezzo tipoMezzo;

    @Basic
    @Column(nullable = false)
    private int quantita;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzoUnitario;
}
