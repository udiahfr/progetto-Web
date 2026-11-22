package it.noleggio.services;

import it.noleggio.entities.Cliente;
import it.noleggio.entities.Gestore;
import it.noleggio.repositories.ClienteRepository;
import it.noleggio.repositories.GestoreRepository;
import it.noleggio.repositories.UtenteRepository;
import it.noleggio.support.exceptions.UtenteGiaRegistratoException;
import it.noleggio.support.exceptions.UtenteNonTrovatoException;
import it.noleggio.support.richieste.RichiestaRegistrazione;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AccountService {
    // Collegamento delle repository con Autowired
    @Autowired
    private UtenteRepository utenteRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private GestoreRepository gestoreRepository;

    // Registrazione del cliente: crea il suo profilo nel nostro DB.
    // L'email arriva dal token di Keycloak, mai dal body.
    // rollbackFor = Exception.class: se si verifica un'eccezione, il DB viene ripristinato.
    @Transactional(rollbackFor = Exception.class)
    public Cliente registra(RichiestaRegistrazione richiesta, String emailToken) throws UtenteGiaRegistratoException{
        String email = emailToken.trim().toLowerCase();
        // Controllo nel caso normale: se l'email esiste già, errore subito
        if (utenteRepository.existsByEmail(email)) throw new UtenteGiaRegistratoException(email);

        Cliente cliente = new Cliente();
        cliente.setNome(richiesta.nome());
        cliente.setCognome(richiesta.cognome());
        cliente.setTelefono(richiesta.telefono());
        cliente.setEmail(email);
        try{
            // saveAndFlush: l'INSERT parte subito, così l'eventuale violazione dell'UNIQUE si cattura qui
            return clienteRepository.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException e){
            // Catch gestisce due registrazioni contemporanee con la stessa email
            // è passato per tutte e due, ma il vincolo UNIQUE del DB ne fa passare una sola
            throw new UtenteGiaRegistratoException(email);
        }
    }

    // Cliente dal token, o indicato dal gestore al banco
    // Si chiama PRIMA di creaPrenotazione: lì il lock sul negozio deve essere la prima query.
    @Transactional(readOnly = true)
    public Cliente getCliente(String email) throws UtenteNonTrovatoException{
        Optional<Cliente> risultato = clienteRepository.findByEmail(email);
        if (risultato.isEmpty())
            throw new UtenteNonTrovatoException(email);
        return risultato.get();
    }

    // Gestore dal token, con il suo negozio
    @Transactional(readOnly = true)
    public Gestore getGestore(String email) throws UtenteNonTrovatoException{
        Optional<Gestore> risultato = gestoreRepository.findByEmail(email);
        if (risultato.isEmpty())
            throw new UtenteNonTrovatoException(email);
        return risultato.get();
    }

}
