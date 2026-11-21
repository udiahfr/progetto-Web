package it.noleggio.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Documento {
    @Id
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{5,20}$", message = "Codice documento non valido")
    private String codice;

    @Basic
    @Column(nullable = false)
    private String nome;

    @Basic
    @Column(nullable = false)
    private String cognome;

    @OneToOne(optional = false)
    @JoinColumn(name = "prenotazione_id", nullable = false, unique = true)
    @JsonIgnore
    private Prenotazione prenotazione;
}
