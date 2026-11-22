package it.noleggio.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Documento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Il documento non può essere identificativo:
    // nel caso in cui lo stesso cliente tornasse più volte nello stesso negozio il DB rifiuta l'aggiunta (esiste già la riga)
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9]{5,20}$", message = "Codice documento non valido")
    @Column(nullable = false, length = 20)
    private String codice;

    @Basic
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String nome;

    @Basic
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String cognome;

    @OneToOne(optional = false)
    @JoinColumn(name = "prenotazione_id", nullable = false, unique = true)
    @JsonIgnore
    private Prenotazione prenotazione;
}
