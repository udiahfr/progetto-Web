package it.noleggio.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Pagamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal importo;

    @Basic
    @Column(nullable = false)
    private LocalDateTime data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CausalePagamento causale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPagamento metodo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "prenotazione_id", nullable = false)
    @JsonIgnore
    private Prenotazione prenotazione;

    @ManyToOne(optional = false)
    @JoinColumn(name = "gestore_id", nullable = false)
    @JsonIgnore
    private Gestore gestore;
}
