package it.noleggio.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
// Strategia per la relazione di ereditarietà: all'interno del DB allochiamo una sola tabella che distingue le classi concrete rispetto all'attributo "ruolo"
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "ruolo", discriminatorType =  DiscriminatorType.STRING, length = 20)
public abstract class Utente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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

    // Bean Validation - controllo del singolo campo senza DB
    @NotBlank // @NotNull lascia passare stringhe vuote
    @Email
    @Basic
    @Column(nullable = false, length = 100, unique = true)
    private String email;

    // Bean Validation
    @NotBlank
    @Pattern(regexp = "^\\+?[0-9]{6,15}$", message = "Numero di telefono inserito non valido")
    @Basic
    @Column(nullable = false, length = 20)
    private String telefono;
}
