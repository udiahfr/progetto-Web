package it.noleggio.support.richieste;

import jakarta.validation.constraints.*;

// Classe per la richiesta di registrazione: definisce cosa può mandare il client quando si registra
// In questo modo il Client non può mandare nella richiesta i campi che non deve settare lui
public record RichiestaRegistrazione(
        @NotBlank @Size(max = 50) String nome,
        @NotBlank @Size(max = 50) String cognome,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{6,15}$", message = "Numero di telefono non valido") String telefono
) {}