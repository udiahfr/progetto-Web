package it.noleggio.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Negozio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "nome", nullable = false, length = 100, unique = true)
    private String nome;

    @Basic
    @Column(name = "indirizzo", nullable = false, length = 200)
    private String indirizzo;

    @Basic
    @Column(name = "longitudine", nullable = false)
    private double longitudine;

    @Basic
    @Column(name = "latitudine", nullable = false)
    private double latitudine;
}
