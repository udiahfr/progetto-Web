package it.noleggio.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
@Entity
public class Orario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Basic
    @Enumerated(EnumType.STRING)
    @Column(name = "giorno", nullable = false)
    private DayOfWeek giorno;

    @Basic
    @Column(name = "apertura", nullable = false)
    private LocalTime apertura;

    @Basic
    @Column(name = "chiusura", nullable = false)
    private LocalTime chiusura;

    // orario[negozio] FK negozio[id]
    @ManyToOne(optional = false)
    @JoinColumn(name = "negozio_id", nullable = false)
    @JsonIgnore
    private Negozio negozio;
}
