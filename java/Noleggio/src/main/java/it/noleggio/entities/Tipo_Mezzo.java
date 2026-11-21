package it.noleggio.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class Tipo_Mezzo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzo_giornaliero_festivo;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzo_giornaliero_feriale;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzo_orario_festivo;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzo_orario_feriale;
}
