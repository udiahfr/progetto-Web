package it.noleggio.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "tipo_mezzo")
public class TipoMezzo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzoGiornalieroFestivo;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzoGiornalieroFeriale;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzoOrarioFestivo;

    @Basic
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzoOrarioFeriale;
}
