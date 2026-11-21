package it.noleggio.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@DiscriminatorValue("GESTORE")
public class Gestore extends Utente {
    @ManyToOne
    @JoinColumn(name = "negozio_id")
    private Negozio negozio;
}
