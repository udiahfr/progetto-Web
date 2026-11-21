# Noleggio bici e monopattini: specifica funzionale

## 1. Idea in breve

Una catena di negozi noleggia biciclette (muscolari ed elettriche) e monopattini.
- Il **cliente** cerca dalla mappa i negozi vicini che hanno i mezzi che gli servono nell'orario scelto, prenota **senza pagare** e paga al ritiro in negozio.
- Il **gestore** del negozio trova la prenotazione, registra il documento del cliente, incassa, consegna i mezzi e ne registra la riconsegna.

### Requisito: due parti transazionali e due parti concorrenti

Il progetto deve avere **due parti transazionali** e **due parti concorrenti**. Sono due cose diverse:
- **transazionale**: un'operazione che scrive su **più dati insieme**, e deve riuscire **tutta o niente**. Se un passo fallisce, il rollback annulla anche i passi già fatti;
- **concorrente**: più utenti agiscono **sugli stessi dati nello stesso momento**, e il sistema deve restare coerente grazie a un **lock**.

Tante operazioni del sistema sono transazionali. Le seguenti sono le più importanti:

| #      | Parte                                                | Cosa succede                                                                                                                                                                                                                     | Perché                                                                                                                                            | Dettagli                                                                           |
| ------ | ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| **T1** | **Ritiro e pagamento**                               | in un'unica transazione: registra il documento, l'ora effettiva di ritiro, l'eventuale penale per ritardo al ritiro, il pagamento, e porta lo stato a `IN_CORSO`                                                                 | se il pagamento fallisce non deve restare un documento registrato su una prenotazione mai partita, né una prenotazione `IN_CORSO` senza pagamento | [[#5.2 Ritiro e pagamento\|5.2]]                                                   |
| **T2** | **Riconsegna e chiusura**                            | in un'unica transazione: registra l'ora effettiva di riconsegna, calcola e salva le penali (ritardo, danni), registra il loro pagamento, porta lo stato a `CONCLUSA` e, se era il primo ordine del cliente, rende valido il codice sconto nato da quella prenotazione | se un passo fallisce, non deve esserci una prenotazione conclusa con penali non registrate, né un codice sconto attivo per un noleggio non chiuso | [[#5.3 Riconsegna e penali\|5.3]], [[#4.7 Conferma, email e codice sconto\|4.7]] |
| **C1** | **Creazione della prenotazione** (online e al banco) | due clienti, o un cliente e un gestore, vogliono l'ultimo mezzo dello stesso negozio nello stesso momento                                                                                                                        | conflitto frequente su una risorsa scarsa, causato da **righe nuove** → **lock pessimistico** sul negozio                                         | [[#7.1 Punto critico 1: creare una prenotazione (online o al banco)\|7.1]]         |
| **C2** | **Modifiche alla stessa prenotazione**               | due gestori dello stesso negozio registrano nello stesso momento il ritiro (o la riconsegna) della stessa prenotazione, es. doppio clic o due dipendenti al banco | conflitto raro sulla **stessa riga** → **lock ottimistico** con `@Version`                                                                        | [[#7.2 Punto critico 2: modifiche concorrenti alla stessa prenotazione\|7.2]]      |

> [!IMPORTANT]
> - Le eccezioni del progetto sono *checked*, quindi T1 e T2 hanno bisogno di `@Transactional(rollbackFor = Exception.class)`. Senza, il rollback **non scatta** ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]).
> - C1 e C2 sono anche transazionali: il lock funziona solo dentro una transazione attiva.
> - La **proroga** ([[#5.8 Proroga del noleggio|5.8]]) usa C1 e C2 insieme.
> - In alternativa a T2 si può mostrare il **trasferimento a un altro negozio** ([[#5.9 Ritardo che coinvolge il cliente successivo|5.9]]): una nuova prenotazione e l'annullamento della vecchia, tutto o niente. È più interessante, ma nelle priorità arriva più avanti ([[#10. Priorità|10]]).

---

## 2. Attori e ruoli

| Ruolo | Chi è | Cosa può fare |
|---|---|---|
| **Cliente** | utente registrato | cercare negozi, prenotare, vedere e annullare le sue prenotazioni, ricevere codici sconto |
| **Gestore** | dipendente di un negozio (**più gestori per negozio**) | vedere e cercare le prenotazioni **del suo negozio**, registrare ritiro e pagamento, riconsegna, penali, prenotazioni al banco, calendario riconsegne, quantità di mezzi in servizio |
| **Amministratore** | chi gestisce la catena | creare negozi, gestori, tipi di mezzo, listini e accessori |

- L'**admin** esiste, perché **tutti i prezzi devono poter essere modificati** da lui: listino dei mezzi, accessori e penali ([[#4.6 Listini prezzi|4.6]], [[#5.3 Riconsegna e penali|5.3]]).
- Ogni negozio ha **più gestori**, per rendere il sistema il più generico possibile. Più dipendenti possono servire più clienti nello stesso momento: è uno dei motivi per cui serve il controllo della concorrenza ([[#7. Database dinamico, concorrenza e transazioni|7]]).
- Un gestore lavora in **un solo** negozio. Il legame gestore → negozio sta nel **nostro database**

---

## 3. Registrazione e accesso

Il cliente si **registra** al primo accesso (nome, cognome, email, telefono) e poi fa il login. Telefono ed email sono già nell'account e non vanno richiesti a ogni prenotazione.

**Perché la registrazione e non i cookie di sessione**
- Il server Spring va progettato **stateless**: finita la richiesta non conserva nulla dell'utente. Tenere i dati del cliente in una sessione lato server va contro questa scelta.
- I cookie stanno sul client e possono essere **modificati o manomessi**. Ci vanno solo informazioni non sensibili. Email e telefono sono dati personali e servono anche al gestore per trovare la prenotazione: devono stare nel DB.
- Con l'account il cliente rivede le sue prenotazioni, le annulla e usa i codici sconto, che sono legati a lui.

> [!NOTE] Autenticazione (decisa, slide Es5 in `archivio/Sviluppo Web/Scala_2025`)
> Come a lezione: **Keycloak** come Authorization Server (OAuth 2.0, token JWT) e il server Spring come **resource server**.
> - Ruoli **cliente**, **gestore**, **admin** come ruoli del client Keycloak (`resource_access`), letti da un `JwtAuthenticationConverter` e controllati con `@PreAuthorize("hasAuthority(...)")` nei controller.
> - Il cliente si registra dalla pagina di Keycloak (registrazione attiva); poi `POST /api/clienti` crea il suo profilo nel nostro DB prendendo l'**email dal token**, mai dal body.
> - Il legame utente Keycloak → riga `utente` è l'email. Il gestore → negozio resta nel nostro DB.
> - Il controller ricava l'id dell'utente con una lettura separata **prima** di chiamare il service: dentro `creaPrenotazione` il lock sul negozio deve restare la prima query ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]).
> - In locale Keycloak gira sulla porta 8081 (realm `noleggio`); il file del realm sta nel progetto.

---

## 4. Flusso del cliente

### 4.1 Mappa e posizione
All'apertura il sito mostra una **mappa** centrata sulla posizione dell'utente (geolocalizzazione del browser). Se l'utente nega il permesso, inserisce un indirizzo o una città.

> [!NOTE] Frontend AI
> Mappa: Leaflet + OpenStreetMap, gratuiti e senza chiave API. La posizione la chiede il **browser** (frontend), poi latitudine e longitudine arrivano al backend come parametri della richiesta (`@RequestParam`: la ricerca è una GET, e una GET non ha body). Il backend non sa "dove sei" da solo.
>
> **Per noi la mappa è solo un endpoint** che riceve latitudine, longitudine e raggio e restituisce i negozi. Si prova tutto con Postman passando `lat`/`lon` a mano. La parte d'esame è il backend REST.
>
> Il frontend si fa **dopo** il backend, il più semplice possibile (il professore ha consigliato Thymeleaf).

### 4.2 Menu di ricerca
L'utente indica:
- **giorno e ora di ritiro**;
- **giorno e ora di riconsegna** (o la durata);
- **quanti mezzi e di che tipo**, anche in combinazione. Esempio: 2 bici elettriche + 1 bici muscolare + 1 monopattino;
- eventuali **preferenze** sul tipo (es. taglia, modello). Gestiamo solo il caso in cui ci siano bambini, indicando con un check e quanti sono (perchè la taglia è più piccola). In pratica la bici per bambini è un **tipo di mezzo a sé**, con le sue quantità per negozio e il suo listino, così rientra nella stessa formula della disponibilità ([[#7. Database dinamico, concorrenza e transazioni|7]]).

Ritiro e riconsegna possono essere effettuati **solo** durante gli orari di apertura dei negozi (entities del DB con orari di apertura e chiusura come attributi).

Gli orari di ritiro e riconsegna sono **liberi, a step di 30 minuti** (9:00, 9:30, 10:00…): il noleggio non è a ore o a giornate fisse. Sono gli stessi step usati per le proposte alternative ([[#4.4 Nessuna disponibilità: proposte alternative|4.4]]). Fa eccezione la proroga gratuita a chi aspetta un ritardo, che dura esattamente quanto il ritardo ([[#5.9 Ritardo che coinvolge il cliente successivo|5.9]]).
### 4.3 Filtro dei negozi sulla mappa
Il backend restituisce, **ordinati per distanza**, solo i negozi che:
1. sono entro il **raggio** scelto;
2. sono **aperti** sia all'ora di ritiro sia a quella di riconsegna;
3. hanno **abbastanza mezzi liberi di ogni tipo richiesto** per **tutto** l'intervallo.

Gli altri spariscono dalla mappa.

### 4.4 Nessuna disponibilità: proposte alternative
Se nessun negozio va bene, il sito propone:
- **spostare l'orario**: il primo orario utile nello stesso negozio, provando ad esempio ogni 30 minuti nella stessa giornata, entro l'orario di apertura;
- **il negozio dove si libera prima**: per ogni negozio vicino, il primo orario in cui la richiesta diventa soddisfacibile.

La ricerca
- è **limitata** (stessa giornata);
- è una sola lettura (`readOnly = true`): il risultato è un **suggerimento**. Quando il cliente prenota, la disponibilità viene ricontrollata con il lock ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]);
- è prioritaria:  prima "primo orario utile nello stesso negozio", poi "negozio dove si libera prima" ([[#10. Priorità|10]]).

### 4.5 Scelta del negozio e form di prenotazione
Il cliente sceglie un negozio e compila il form:
- combinazione di mezzi (precompilata dalla ricerca, modificabile);
- orario di ritiro e di riconsegna;
- **codice sconto**, se ne ha uno;
- riepilogo del **prezzo stimato**.

Telefono ed email sono già nell'account ([[#3. Registrazione e accesso|3]]).

Gli **accessori a pagamento** (caschi, seggiolini, lucchetti, cestini, porta-telefono con powerbank…) **non** sono nel form: sono prenotabili **solo** in negozio al momento del ritiro, e li aggiunge solo il gestore ([[#5.2 Ritiro e pagamento|5.2]]). **Ogni negozio ha le sue quantità** di ogni accessorio, quindi sono a numero limitato ed entrano nel calcolo della disponibilità con la stessa formula dei mezzi ([[#7. Database dinamico, concorrenza e transazioni|7]]).

> [!warning] 
> Prenotare è **gratis**, quindi un cliente potrebbe bloccare tutti i mezzi di un negozio. Servono dei **limiti**: massimo **2 prenotazioni attive** per cliente e massimo **10 mezzi** per prenotazione. Se si superano → eccezione dedicata.

### 4.6 Listini prezzi
Ogni **tipo di mezzo** ha il suo listino; anche gli accessori hanno un prezzo.

- **tutti i prezzi li modifica l'admin** (listino dei mezzi, accessori, penali). Nessun prezzo è scritto nel codice: stanno tutti nel database.
- **i prezzi sono uguali in tutti i negozi**: un solo listino per tutta la catena, non uno per negozio.
- **prezzi più alti nel fine settimana e nei festivi**, sia per il listino sia per le penali ([[#5.3 Riconsegna e penali|5.3]]). Quindi ogni prezzo ha due valori: feriale e fine settimana/festivo.

I **festivi** non si calcolano da soli: nel database c'è un elenco delle date festive, che l'admin può modificare ([[#8.2 Endpoint per parte|8.2]]). È un'informazione da prevedere nell'E-R.

**Formula del listino (decisa).** L'admin imposta solo i **numeri** dalla sua interfaccia; la **formula**, cioè quali prezzi esistono, è fissata nel codice. Ogni tipo di mezzo ha quattro prezzi: **orario** e **giornaliero**, ciascuno feriale e fine settimana/festivo.
- Si pagano le **ore iniziate** × prezzo orario, ma per ogni giornata di noleggio mai più del **prezzo giornaliero** (tetto).
- Feriale o fine settimana/festivo lo decide il **giorno del ritiro**.
- Proroga ([[#5.8 Proroga del noleggio|5.8]]): il prezzo in più è prezzo(ritiro → nuova riconsegna) − prezzo(ritiro → vecchia riconsegna), con le tariffe del momento della proroga e mai sotto zero. Prezzare solo il tempo aggiunto ignorerebbe il tetto giornaliero.
- Gli accessori hanno un prezzo per tutto il noleggio (feriale e festivo), non orario.

> [!IMPORTANT] Prezzo fissato al momento della prenotazione
> Visto che l'admin può cambiare i prezzi in qualsiasi momento, se il listino cambia tra la prenotazione e il ritiro il cliente paga quanto gli è stato mostrato. Quindi la prenotazione deve **conservare il prezzo unitario** di ogni mezzo e accessorio, non solo un riferimento al listino. È lo stesso motivo per cui in un e-commerce l'ordine salva il prezzo pagato.

### 4.7 Conferma, email e codice sconto
Il cliente conferma. Il sistema:
1. ricontrolla la disponibilità **con il lock** e salva la prenotazione (stato `PRENOTATA`);
2. se è stato usato un codice sconto, lo segna come **usato** (nella **stessa** transazione);
3. se l'utente ha effettuato il suo primo ordine, genera un **nuovo codice sconto** per il prossimo ordine;
4. invia un'**email** di conferma con riepilogo, orario di ritiro, indirizzo del negozio e nuovo codice sconto (ove generato).

Regole codice sconto: percentuale fissa (10%), **un solo uso**, valido solo per il cliente che l'ha ricevuto. Se la prenotazione su cui è stato **usato** viene annullata, il codice torna utilizzabile; se scade per mancato ritiro, no.

> [!IMPORTANT] Gestione dell'invio di mail
> L'invio di email si fa con `spring-boot-starter-mail` (`JavaMailSender`). In sviluppo si usa un server finto (es. Mailpit) o, più semplice, si scrive la mail nel **log**.
>
> Regola importante: **la mail si manda solo dopo il commit**, mai dentro la transazione che tiene il lock.
> - Se la transazione va in rollback dopo l'invio, il cliente riceve la conferma di una prenotazione che non esiste.
> - L'invio è lento e terrebbe il lock occupato inutilmente.
> - Se la mail fallisce, la prenotazione **resta valida**: si segnala l'errore, non si annulla.

### 4.8 Annullamento e mancato ritiro
- **Annullamento**: il cliente annulla dalla sua area fino a un certo tempo prima del ritiro (3 ore). Stato → `ANNULLATA`, i mezzi tornano disponibili.
- **Finestra di ritiro**: il ritiro si può registrare da poco prima dell'orario previsto (30 minuti) fino alla tolleranza dopo (30 minuti). Non si ritira oggi una prenotazione di dopodomani.
- **Ritiro in ritardo, entro la tolleranza**: il cliente ritira, ma **la riconsegna non slitta**: resta all'orario prenotato, perché dopo quell'orario i mezzi possono essere già promessi ad altri. Paga comunque il periodo prenotato per intero, più una **penale per ritardo al ritiro** ([[#5.3 Riconsegna e penali|5.3]]). Se vuole più tempo, chiede una **proroga** ([[#5.8 Proroga del noleggio|5.8]]).
- **Mancato ritiro (no-show)**: se il cliente non si presenta entro la tolleranza, la prenotazione è **scaduta** e i mezzi tornano liberi. Nessuna penale: la prenotazione è gratuita e non c'è nulla da incassare.

> [!IMPORTANT] Realizzazione della scadenza
> **La scadenza si decide con una condizione calcolata**, senza aspettare un job:
>
> **scaduta** = stato `PRENOTATA` **e** ora di ritiro + tolleranza < adesso
>
> La stessa condizione va usata in **tutti** i punti:
> - nella query di disponibilità (le scadute non occupano mezzi);
> - in `registraRitiro` e in `annulla` (una scaduta non si ritira e non si annulla);
> - nell'elenco del gestore (mostrata come "scaduta", non come "prenotata").
>
>**Verifica**: un job periodico `@Scheduled` (lo stesso del promemoria, [[#4.9 Promemoria via email un'ora prima del ritiro|4.9]]) controlla le condizioni e in caso aggiorna lo stato a `SCADUTA`, così nel database resta scritto. I controlli elencati sopra però usano **sempre** la condizione calcolata: tra la scadenza e il giro successivo del job lo stato scritto è ancora `PRENOTATA`. Il job aggiorna con una `@Query` condizionale ("solo se è ancora `PRENOTATA`"), così non sovrascrive un'operazione arrivata nel frattempo.

### 4.9 Promemoria via email un'ora prima del ritiro
Un'ora prima dell'orario di ritiro il sito manda al cliente un'email di promemoria: negozio, indirizzo, orario, mezzi prenotati.

> [!NOTE] Realizzazione del promemoria
> - Serve un **job periodico** con `@Scheduled` (più `@EnableScheduling` nella configurazione) che gira ad esempio ogni 5 minuti.
> - Il job cerca le prenotazioni `PRENOTATA` con ritiro tra adesso e adesso + 1 ora, per cui **il promemoria non è ancora stato inviato**.
> - Per ognuna manda la mail e segna "promemoria inviato". Nell'E-R la prenotazione deve avere un'informazione in più: promemoria inviato sì/no (o quando).
> - L'informazione si segna solo **dopo** l'invio riuscito: se la mail fallisce, il giro successivo ci riprova.
> - Se il cliente annulla nel frattempo, la prenotazione non è più `PRENOTATA` e il promemoria non parte.
> - Le prenotazioni fatte meno di un'ora prima del ritiro (es. al banco) non ricevono promemoria ma solo conferma della prenotazione.
>
> Come per la mail di conferma, in sviluppo basta scrivere la mail nel log.

---

## 5. Flusso del gestore

### 5.1 Login ed elenco prenotazioni
Il gestore fa il login e vede le prenotazioni **del suo negozio**. Di default vede quelle di oggi; ha **filtri** per stato e data e una ricerca per **nome, cognome o telefono** del cliente. L'elenco è **paginato**.

> [!IMPORTANT]
> Il gestore **non deve vedere** le prenotazioni degli altri negozi. Il controllo va fatto lato server, nel service: "la prenotazione appartiene al negozio di questo gestore?".

### 5.2 Ritiro e pagamento
Quando il cliente arriva in negozio, il gestore:
1. apre la prenotazione;
2. registra il **documento d'identità** (numero, nome e cognome), che il negozio trattiene a garanzia per eventuali penali (danni, ritardo). Non c'è **cauzione**: la garanzia è il documento;
3. consegna i mezzi prenotati, scegliendoli tra quelli del tipo giusto. Il sistema **non** registra quale bici precisa viene data: si lavora con le **quantità per categoria** ([[#5.6 Gestione dei mezzi|5.6]] e la nota qui sotto);
4. il sistema registra l'**ora effettiva di ritiro**; se è dopo l'orario previsto (ma entro la tolleranza), aggiunge la **penale per ritardo al ritiro** ([[#5.3 Riconsegna e penali|5.3]]);
5. vede il **totale** (mezzi + accessori − sconto + eventuale penale) e registra il **pagamento** (importo, metodo: contanti o carta, data, gestore che l'ha incassato). Il pagamento è **simulato**: niente circuiti di pagamento veri;
6. stato → `IN_CORSO`.

> [!NOTE] Scelta di progetto
> Al ritiro non c'è il lock pessimistico:
> - Se il sistema registrasse **quale bici precisa** consegna, il ritiro sarebbe un punto di concorrenza: due gestori dello stesso negozio potrebbero dare la stessa bici a due clienti diversi. Il `@Version` sulla prenotazione non lo vedrebbe, perché sono **due prenotazioni diverse**, e servirebbe lo stesso lock sul negozio della creazione ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]).
> - Abbiamo scelto le **quantità per categoria**: al ritiro non si assegna nessuna bici precisa, quindi quel conflitto non esiste. Le quantità sono già state "riservate" in modo sicuro alla creazione della prenotazione.
> - Al ritiro resta solo il lock **ottimistico** (`@Version`), per il caso "due gestori registrano nello stesso momento lo stesso ritiro" ([[#7.2 Punto critico 2: modifiche concorrenti alla stessa prenotazione|7.2]]).

**Accessori aggiunti in negozio.** Al ritiro il cliente può chiedere accessori. Visto che gli accessori sono a numero limitato per negozio ([[#4.5 Scelta del negozio e form di prenotazione|4.5]]), aggiungerli è come prenotarli: bisogna controllare che siano liberi per tutto l'intervallo del noleggio.

> [!IMPORTANT]
> L'aggiunta di accessori è un'**operazione separata** dal ritiro, con un suo metodo del service, e usa il **lock pessimistico sul negozio** come la creazione della prenotazione ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]). Così `registraRitiro` resta senza lock pessimistico, come deciso sopra. Il prezzo degli accessori aggiunti si fissa al momento dell'aggiunta ([[#4.6 Listini prezzi|4.6]]).

> [!WARNING] Privacy
> Il numero del documento è un dato personale.
> - Salviamo il minimo indispensabile (niente foto del documento): solo numero identificativo e nome e cognome;
> - Lo vede solo il gestore di quel negozio: **non** va restituito nelle risposte JSON al cliente. Il progetto del professore restituisce direttamente le entità; qui va deciso cosa **non** serializzare.
> 

### 5.3 Riconsegna e penali
Alla riconsegna il gestore:
- registra **data e ora effettive** di riconsegna;
- se c'è **ritardo**, il sistema calcola la **penale** (es. tariffa oraria × ore di ritardo);
- se ci sono **danni**, il gestore indica **quanti mezzi di ogni tipo** sono stati danneggiati (più una descrizione facoltativa), e il sistema calcola la penale;
- registra il pagamento delle penali e restituisce il documento;
- stato → `CONCLUSA` (e il codice sconto di quella prenotazione diventa valido, [[#4.7 Conferma, email e codice sconto|4.7]]);
- i mezzi danneggiati vengono tolti dal servizio ([[#5.6 Gestione dei mezzi|5.6]]).

La penale la paga chi ha prenotato, quindi basta sapere **quanti** mezzi sono danneggiati e **di quanto** è il ritardo: non serve sapere quale bici precisa.

**Riepilogo delle penali**

| Penale | Quando | Quando si paga |
|---|---|---|
| **Ritardo al ritiro** | il cliente ritira dopo l'orario previsto, ma entro la tolleranza ([[#4.8 Annullamento e mancato ritiro\|4.8]]) | al ritiro, insieme al noleggio ([[#5.2 Ritiro e pagamento\|5.2]]) |
| **Ritardo alla riconsegna** | il cliente riconsegna più di **10 minuti** dopo l'orario previsto (eventualmente prorogato, [[#5.8 Proroga del noleggio\|5.8]]) | alla riconsegna |
| **Danni** | il gestore indica quanti mezzi di ogni tipo sono danneggiati | alla riconsegna |

- Oltre la tolleranza al ritiro non c'è penale: la prenotazione scade ([[#4.8 Annullamento e mancato ritiro|4.8]]).
- **tolleranza di 10 minuti alla riconsegna**. Entro i 10 minuti non si paga niente.
- Per le penali di ritardo servono nei dati gli **orari effettivi** di ritiro e riconsegna, oltre a quelli previsti.

**Importo delle penali di ritardo**.
- Dipende dalla **durata del ritardo**, a scaglioni di 15 minuti: una cifra per ogni scaglione iniziato.
- La cifra è diversa **nel fine settimana** e **nei giorni feriali**: il "fine settimana" comprende sabato, domenica e **festivi**. Per la penale, il giorno che decide tra feriale e fine settimana è quello della **riconsegna prevista**.
- Le cifre le imposta l'**admin**, come tutti gli altri prezzi
- Superati i 10 minuti, il ritardo si conta **dall'orario previsto**.

**Esempio** (cifre inventate): feriale 2 € ogni 15 minuti iniziati, fine settimana/festivo 3 €. Una riconsegna prevista sabato alle 13:00 e fatta alle 13:40 è oltre i 10 minuti. Il ritardo si conta dalle 13:00, quindi 40 minuti = 3 scaglioni iniziati × 3 € = 9 €. Fatta alle 13:08, invece, non si paga nulla.

L'importo della penale per i **danni** esiste e l'importo lo imposta l'admin.

### 5.4 Calendario delle riconsegne
Per ogni negozio, un **calendario** delle riconsegne previste (giorno, settimana) con:
- quali mezzi rientrano e a che ora;
- le riconsegne **in ritardo**, evidenziate (previste nel passato e non ancora registrate).

Al gestore serve per sapere quando avrà mezzi liberi da dare a chi entra in negozio.

### 5.5 Prenotazione al banco (cliente senza prenotazione)
Se un cliente entra in negozio senza prenotazione, il gestore crea la prenotazione per lui e procede subito con ritiro e pagamento ([[#5.2 Ritiro e pagamento|5.2]]).

> [!IMPORTANT] Gestione prenotazione al banco
> - La prenotazione al banco passa per **lo stesso metodo del service** (con lo stesso lock) usato dalle prenotazioni online. Altrimenti il gestore può dare al banco l'ultima bici mentre un cliente la sta prenotando dal sito ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]).
> - Il metodo riceve il **cliente come parametro**: online il controller lo ricava dal token, al banco lo indica il gestore.
> - Creazione e ritiro sono **due chiamate separate** dal controller, quindi due transazioni. Va bene: in mezzo la prenotazione è semplicemente `PRENOTATA`. Non far chiamare `creaPrenotazione` da un altro metodo della stessa classe: la chiamata interna non passa dal proxy di Spring e l'annotazione `@Transactional` del metodo chiamato viene ignorata.
> - **Gestione registrazione utente**: prima che il gestore possa creare la prenotazione, il cliente effettua la registrazione lì davanti e poi il gestore compila il form di prenotazione trovando l'account con la mail.

### 5.6 Gestione dei mezzi
Ogni negozio ha una **quantità per tipo di mezzo** (es. 8 elettriche, 5 muscolari, 3 monopattini). Il gestore (o l'admin) può:
- **aumentare** la quantità (mezzi nuovi, mezzi riparati che tornano in servizio);
- **diminuirla** (mezzi rotti, in manutenzione).

> [!WARNING] Disponibilità del magazzino
> Diminuire la quantità **riduce la disponibilità futura** di quel tipo.
> - È **sempre permesso** (una bici rotta è rotta).
> - Si fa sotto lo **stesso lock sul negozio** della creazione delle prenotazioni ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]). Altrimenti una prenotazione potrebbe contare la bici che nello stesso momento viene tolta.
> - Dopo la modifica si ricalcola se le prenotazioni future di quel tipo sono ancora coperte. La risposta riporta l'elenco delle prenotazioni **rimaste scoperte**, che il gestore dovrà gestire ([[#5.7 Prenotazioni che il negozio non può onorare|5.7]]).
> - Non si può scendere sotto il numero di mezzi di quel tipo **fuori in questo momento** con noleggi `IN_CORSO`.

### 5.7 Prenotazioni che il negozio non può onorare
Tre situazioni portano a una prenotazione che il negozio non riesce più a coprire:
- un mezzo va fuori servizio;
- un cliente precedente non ha ancora riconsegnato;
- l'admin cambia l'orario di apertura e la prenotazione resta fuori orario ([[#5.10 Orari di apertura|5.10]]).

Il gestore può **annullare la prenotazione da parte del negozio**: stato `ANNULLATA` con motivo "negozio". Il cliente viene avvisato e il suo eventuale codice sconto usato torna valido.

### 5.8 Proroga del noleggio
Se il cliente vuole più tempo, la riconsegna **non slitta da sola**: si chiede una **proroga**.
- La chiede il **cliente** dall'app o il **gestore** in negozio, su una prenotazione `PRENOTATA` (non scaduta) o `IN_CORSO`.
- Si indica il **nuovo orario di riconsegna**, più tardi di quello attuale e dentro l'orario di apertura.
- Il sistema controlla che i mezzi siano liberi nel **tempo aggiunto**, cioè tra la vecchia e la nuova riconsegna. Se lo sono, la proroga viene concessa; altrimenti `DisponibilitaInsufficienteException`.
- **Si paga di più**: il tempo aggiunto si calcola con il listino. Il prezzo viene fissato **al momento della proroga** e salvato, come per la prenotazione ([[#4.6 Listini prezzi|4.6]]).
  - Se la prenotazione è `PRENOTATA`, il prezzo in più si aggiunge al totale da pagare al ritiro.
  - Se è `IN_CORSO`, cioè già pagata, l'importo della proroga si paga alla riconsegna, insieme alle eventuali penali.
- La proroga **non** si può chiedere dopo che l'orario di riconsegna è passato. Da quel momento è un ritardo e si paga la penale ([[#5.3 Riconsegna e penali|5.3]]).

> [!IMPORTANT] Concorrenza
> La proroga è il caso più completo del progetto, perché usa **tutti e due i lock**.
> - **Lock pessimistico sul negozio.** Allungare l'intervallo è come prenotare di nuovo per il tempo aggiunto: due proroghe, oppure una proroga e una nuova prenotazione, possono contendersi l'ultimo mezzo. Si usa lo **stesso lock sul negozio** della creazione ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]), come prima query della transazione.
> - **Lock ottimistico (`@Version`).** Si modifica la riga della prenotazione, che nello stesso momento potrebbe essere annullata o riconsegnata.
>
> Nel conteggio, quando si controlla la disponibilità per il tempo aggiunto, la query **non deve contare la prenotazione stessa**, altrimenti i mezzi del cliente risultano occupati da lui stesso.

### 5.9 Ritardo che coinvolge il cliente successivo
Se un cliente non riconsegna entro la tolleranza di 10 minuti, i suoi mezzi possono mancare a chi ha prenotato subito dopo nello stesso negozio.
1. Il sistema individua le prenotazioni `PRENOTATA` dello stesso negozio che **restano scoperte** a causa del ritardo, cioè che non hanno più abbastanza mezzi contando come ancora fuori quelli in ritardo (regola dei ritardi, [[#7. Database dinamico, concorrenza e transazioni|7]]).
2. Il cliente successivo viene **avvisato via email**: i mezzi potrebbero non essere pronti all'orario previsto.
3. In negozio, il gestore gli propone **due opzioni affiancate**:
   - **aspettare**, con un compenso:
     - se nel negozio è disponibile, riceve **gratis il porta-telefono con powerbank**;
     - se non è disponibile, la sua prenotazione viene **prolungata gratis** di un tempo pari al ritardo (es. aspetta 25 minuti → riconsegna spostata di 25 minuti senza costi). È una proroga gratuita ([[#5.8 Proroga del noleggio|5.8]]): serve che i mezzi siano liberi nel tempo aggiunto;
   - **andare all'altro negozio più vicino** che ha disponibilità per la sua richiesta: il **gestore** fa per lui la prenotazione in quel negozio e annulla quella originale (motivo "trasferimento").
4. Il cliente può comunque **annullare** senza costi, anche oltre il limite normale ([[#4.8 Annullamento e mancato ritiro|4.8]]).
5. Il cliente in ritardo paga la penale di ritardo alla riconsegna ([[#5.3 Riconsegna e penali|5.3]]).

> [!WARNING] Trasferimento ad un altro negozio
> Il trasferimento a un altro negozio è un'eccezione alla regola "il gestore vede solo il suo negozio" ([[#5.1 Login ed elenco prenotazioni|5.1]]).
> - Il gestore può **creare** una prenotazione in un altro negozio solo in questo caso, cioè partendo da una prenotazione del suo negozio coinvolta in un ritardo.
> - Non può vedere né modificare le altre prenotazioni di quel negozio.
>
> Creare la nuova prenotazione e annullare la vecchia avviene in **un'unica transazione**: o riescono tutte e due, o nessuna. Altrimenti il cliente potrebbe restare con due prenotazioni o con nessuna. La nuova prenotazione usa il lock pessimistico sul **negozio di destinazione**, perché è lì che si contendono i mezzi ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]). La vecchia usa `@Version`.

> [!NOTE] Realizzazione
> - **Chi se ne accorge.** Lo stesso job periodico del promemoria ([[#4.9 Promemoria via email un'ora prima del ritiro|4.9]], `@Scheduled`) cerca anche i noleggi `IN_CORSO` con riconsegna prevista passata da più di 10 minuti. Per il loro negozio ricalcola la disponibilità delle prossime prenotazioni e avvisa quelle scoperte.
> - **Cosa si salva.** La prenotazione avvisata va **segnata** ("coinvolta in un ritardo"), per due motivi: non mandare la mail a ogni giro del job, e sapere al ritiro che le spetta l'accessorio gratis.
> - **Al ritiro.** Il gestore vede il segno e applica il compenso:
>   - il porta-telefono con powerbank si aggiunge **a prezzo zero**, con l'aggiunta di accessori in negozio ([[#5.2 Ritiro e pagamento|5.2]]). Il prezzo zero si salva come ogni altro prezzo fissato ([[#4.6 Listini prezzi|4.6]]), così resta traccia che è stato regalato;
>   - oppure si fa la proroga gratuita ([[#5.8 Proroga del noleggio|5.8]]), con prezzo zero per il tempo aggiunto.
> - **Annullamento.** Una prenotazione segnata si può annullare anche oltre il limite normale (le 3 ore prima del ritiro, [[#4.8 Annullamento e mancato ritiro|4.8]]), perché il problema non è del cliente.
> - **Mail dopo il commit.** Come per le altre email: prima si salva il segno, poi si manda la mail ([[#4.7 Conferma, email e codice sconto|4.7]]).
> - il porta-telefono con powerbank **non si prenota dal sito**: lo aggiunge solo il gestore in negozio ([[#4.5 Scelta del negozio e form di prenotazione|4.5]]). Quindi il caso "l'aveva già prenotato" non esiste;
> - se **non c'è né il porta-telefono né il tempo** per la proroga gratuita, il cliente sceglie il trasferimento o annulla o aspetta;
> - con il trasferimento il prezzo **non cambia**, perché i prezzi sono uguali in tutti i negozi ([[#4.6 Listini prezzi|4.6]]).

### 5.10 Orari di apertura
Ogni negozio ha **una fascia continuata** per giorno della settimana (es. lunedì 9:00–19:00). Un giorno senza orario = negozio chiuso. I festivi non chiudono il negozio: valgono l'orario del giorno della settimana e il listino festivo ([[#4.6 Listini prezzi|4.6]]).
- Il cliente vede gli orari del negozio per scegliere la finestra di ritiro e riconsegna.
- L'**admin** può modificare l'orario di un giorno o toglierlo (negozio chiuso quel giorno).

> [!WARNING] Modifica degli orari
> - Si fa sotto lo **stesso lock sul negozio** della creazione delle prenotazioni ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]). Altrimenti una prenotazione potrebbe essere controllata con l'orario vecchio mentre viene cambiato.
> - Chiusura non dopo l'apertura → `OrarioNonValidoException`.
> - La modifica è sempre permessa. La risposta riporta l'elenco delle prenotazioni future **rimaste fuori orario**, che il gestore dovrà gestire ([[#5.7 Prenotazioni che il negozio non può onorare|5.7]]).

---

## 6. Stati della prenotazione

```
PRENOTATA ──► IN_CORSO ──► CONCLUSA
    │
    ├──► ANNULLATA   (dal cliente, o dal negozio con motivo)
    │
    └──► SCADUTA     (scritto dal job; i controlli usano la condizione calcolata, vedi 4.8)
```

- Ogni operazione controlla lo **stato di partenza** e, per `PRENOTATA`, che **non sia scaduta**. Esempi: non si paga una prenotazione annullata o scaduta; non si annulla una prenotazione `IN_CORSO`. Uno stato sbagliato dà un'eccezione dedicata.
- Occupano mezzi solo `PRENOTATA` non scaduta e `IN_CORSO`.
- La proroga ([[#5.8 Proroga del noleggio|5.8]]) non cambia lo stato: sposta solo l'orario di riconsegna.
- Semplificazione voluta: **nessun tempo di rimessa** tra due noleggi. Se A finisce alle 13:00, B può iniziare alle 13:00.

---

## 7. Database dinamico, concorrenza e transazioni

> [!IMPORTANT] Database dinamico
> La disponibilità **non è un numero salvato** che si decrementa, perché i mezzi **tornano** liberi alla riconsegna e la disponibilità dipende dall'**intervallo di tempo**.
> La disponibilità si **calcola** ogni volta:
>
> **mezzi liberi di tipo T nel negozio N tra inizio e fine** = quantità di tipo T in servizio in N − mezzi di tipo T impegnati in prenotazioni attive di N **che si sovrappongono** a [inizio, fine)
>
> Due intervalli si sovrappongono se `inizioA < fineB` **e** `inizioB < fineA`.
>
> La **stessa formula vale per gli accessori**: quantità di quell'accessorio nel negozio − accessori impegnati in prenotazioni attive sovrapposte ([[#4.5 Scelta del negozio e form di prenotazione|4.5]]).

> [!NOTE] Ritardi
> Un noleggio `IN_CORSO` la cui fine prevista è già passata non ha ancora una riconsegna effettiva, quindi non si può usare "fino alla riconsegna" nel calcolo. Per risolvere questo problema, un noleggio `IN_CORSO` in ritardo occupa i mezzi **fino ad adesso + un margine** (es. 1 ora), ricalcolato a ogni ricerca. Se il ritardo rende scoperta una prenotazione successiva, il cliente viene avvisato via email e, se aspetta, riceve un compenso (porta-telefono in omaggio o proroga gratuita) ([[#5.9 Ritardo che coinvolge il cliente successivo|5.9]]). Il gestore lo vede nel calendario ([[#5.4 Calendario delle riconsegne|5.4]]) e, se serve, può annullare la prenotazione ([[#5.7 Prenotazioni che il negozio non può onorare|5.7]]).

### 7.1 Punto critico 1: creare una prenotazione (online o al banco)

**Il problema.** Due clienti vogliono l'ultima bici elettrica dello stesso negozio nello stesso orario. Tutti e due:
1. contano le prenotazioni sovrapposte → "ne resta 1";
2. inseriscono la loro prenotazione.

Risultato: due prenotazioni per una bici sola.

**Perché `@Transactional` non basta.** `@Transactional` rende l'operazione **atomica** (tutto o niente), ma non impedisce che due transazioni leggano lo stesso stato prima che una delle due scriva.

Non è il *lost update* classico, in cui due transazioni modificano **la stessa riga già esistente**. Qui nessuna riga esistente viene modificata: ognuna decide in base a un **insieme di righe** (le prenotazioni sovrapposte), e l'altra ci **aggiunge una riga nuova** che avrebbe cambiato quella decisione. È un'anomalia dovuta a **righe nuove**, parente del *phantom read*. Un isolamento come `REPEATABLE_READ` **non la impedisce**.

Il lock sulle righe contate è scartato perché non blocca chi sta per **inserire** righe nuove. Su alcuni DBMS (MySQL) blocca anche gli "spazi" tra le righe, ma il comportamento dipende dal DBMS e porta facilmente a **deadlock**.

**Soluzioni possibili**

| Soluzione                                                | Come                                                                                                                                                                                                                                                                | Pro                                                                                | Contro                                                                                                                                     |
| -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| **A. Lock pessimistico su una riga "àncora"** (proposta) | Prima di tutto, si legge con `PESSIMISTIC_WRITE` una riga **che esiste già** e che tutte le operazioni sullo stesso negozio devono leggere: il **negozio**. Chi arriva secondo aspetta il commit del primo, poi conta e vede anche la prenotazione appena inserita. | semplice; il lock riguarda un solo negozio, gli altri negozi lavorano in parallelo | le prenotazioni dello stesso negozio vengono serializzate (accettabile: sono poche al secondo)                                             |
| B. Lock sulla riga "negozio + tipo di mezzo"             | come A, ma più fine: si bloccano solo i tipi richiesti. Con le quantità per categoria questa riga esiste già (è quella che dice "8 elettriche")                                                                                                                     | più parallelismo                                                                   | con richieste miste (elettrica + muscolare) si bloccano più righe, e bisogna farlo **sempre nello stesso ordine** per evitare **deadlock** |
| C. Isolamento `SERIALIZABLE`                             | `@Transactional(isolation = SERIALIZABLE)`                                                                                                                                                                                                                          | non serve pensare a quale riga bloccare                                            | il più costoso; il DB può far fallire una delle due transazioni per deadlock, e il comportamento dipende dal DBMS                          |

Ai fini della risoluzione del problema si utilizza la soluzione **A**. 

Il negozio **non viene modificato**: fa solo da "semaforo" per serializzare le operazioni, perché la disponibilità è **calcolata** e non salvata.

**Schema del metodo** (nel service, chiamato sia dal controller del cliente sia da quello del gestore):
1. `@Transactional(isolation = READ_COMMITTED, rollbackFor = Exception.class)` (vedi le regole sotto);
2. **come prima query in assoluto**, legge il negozio con lock `PESSIMISTIC_WRITE`. Nel repository serve un **metodo dedicato**, ad esempio `findByIdPerAggiornamento`, con `@Query` e `@Lock(LockModeType.PESSIMISTIC_WRITE)`. In SQL diventa `SELECT … FOR UPDATE`. Se il negozio non esiste → `NegozioNonTrovatoException`;
3. solo **dopo** il lock: controllo permessi (al banco, il negozio è del gestore?), cliente, orari di apertura, intervallo valido, limiti per cliente;
4. conta per ogni tipo richiesto i mezzi liberi nell'intervallo **con la `@Query` di conteggio**; se non bastano lancia `DisponibilitaInsufficienteException`;
5. valida e consuma il codice sconto ([[#7.3 Punto critico 3: codice sconto usato una volta sola|7.3]]);
6. calcola e salva i prezzi unitari; salva la prenotazione;
7. se è il primo ordine del cliente, genera il nuovo codice sconto (non ancora valido, [[#4.7 Conferma, email e codice sconto|4.7]]);
8. commit → il lock viene rilasciato → **dopo** si manda l'email.

> [!WARNING] Regole 
> - **Il lock è la prima query della transazione.** Su MySQL (InnoDB, isolamento di default `REPEATABLE_READ`) la transazione "fotografa" il database alla prima lettura normale. Se prima del lock si legge qualcos'altro (il cliente, il negozio senza lock, il codice sconto), il conteggio fatto dopo l'attesa usa la foto **vecchia** e non vede la prenotazione appena inserita dall'altro: doppia prenotazione **nonostante il lock**.
> - **`isolation = READ_COMMITTED` sui metodi critici** (`creaPrenotazione`, `prorogaPrenotazione`, `aggiungiAccessori`, `trasferisciPrenotazione`, `cambiaQuantitaMezzi`, `cambiaQuantitaAccessori`, `modificaOrario`, `rimuoviOrario`). È una rete di sicurezza: ogni query vede i dati già committati, anche se qualcuno sbaglia l'ordine. Si collega a quanto visto a lezione sulla scelta del livello di isolamento per ogni funzione.
> - **Il conteggio è sempre una query**, mai la navigazione di una collezione già caricata (es. `negozio.getPrenotazioni()`). Hibernate restituirebbe dati in memoria di prima del lock.
> - **Metodo dedicato per il lock.** Non mettere `@Lock` su `findById`, altrimenti **ogni** lettura del negozio (anche le ricerche `readOnly`) prende il lock. E **mai `PESSIMISTIC_READ`**: è un lock condiviso, quindi le due transazioni lo otterrebbero entrambe e il problema resterebbe.

> [!WARNING] Eccezioni checked e rollback
> Abbiamo deciso eccezioni *checked* (`extends Exception`). Ma **Spring di default fa rollback solo per le eccezioni unchecked**. Con una checked la transazione viene **committata** lo stesso, anche se il metodo è uscito con un errore: quindi ogni metodo del service che scrive ha `@Transactional(rollbackFor = Exception.class)`.
> Un `@Transactional` sul metodo **sostituisce** quello messo sulla classe, quindi va ripetuto. Altrimenti, ad esempio, un codice sconto segnato come usato resta usato anche se la prenotazione fallisce dopo.

> [!NOTE] Lock non ottenuto in tempo
>  Se il lock non arriva in tempo (o c'è un deadlock), JPA lancia `LockTimeoutException`. Passando da un repository Spring, però, l'eccezione arriva **tradotta** in `PessimisticLockingFailureException` (package `org.springframework.dao`): un `catch (LockTimeoutException …)` non scatterebbe.
> - È unchecked: uscendo dal metodo `@Transactional`, Spring fa comunque rollback.
> - Nel controller: `catch (PessimisticLockingFailureException e)` → "riprova".
> - Con `@Transactional(timeout = …)` di pochi secondi una richiesta non resta appesa 50 secondi.

### 7.2 Punto critico 2: modifiche concorrenti alla stessa prenotazione
**Problema.** Due gestori dello stesso negozio registrano **nello stesso istante** il ritiro (o la riconsegna) della stessa prenotazione: un doppio clic, o due dipendenti che servono lo stesso cliente. Senza controllo verrebbero registrati due documenti e due pagamenti.

Il caso "il gestore registra il ritiro mentre il cliente annulla" **di norma non può succedere**: il cliente annulla fino a 3 ore prima del ritiro, il gestore registra il ritiro solo da 30 minuti prima ([[#4.8 Annullamento e mancato ritiro|4.8]]), quindi le due finestre non si sovrappongono. Fanno eccezione le prenotazioni **coinvolte in un ritardo**, che il cliente può annullare anche oltre le 3 ore ([[#5.9 Ritardo che coinvolge il cliente successivo|5.9]]), e l'annullamento fatto dal gestore in negozio: anche in questi casi il `@Version` fa vincere solo la prima operazione.

Per risolvere questo problema si implementa un **lock ottimistico** con un campo `@Version` sulla prenotazione. I conflitti sono **rari**, quindi non conviene bloccare. Chi salva per secondo riceve un'eccezione, la transazione fa rollback e il controller risponde ("la prenotazione è stata modificata, ricarica").

> [!WARNING] Dove si cattura l'eccezione ottimistica
> - Nasce al **flush**: al commit, dopo l'ultima riga del service, oppure prima se il service chiama `saveAndFlush`. In entrambi i casi si cattura nel controller.
> - Arriva al controller come `ObjectOptimisticLockingFailureException` di Spring (sottoclasse di `OptimisticLockingFailureException`), non come `OptimisticLockException` di JPA.
> - È **unchecked**: l'IDE non ce la ricorda, e se la dimentichiamo diventa un 500.
>
> Ogni controller che chiama un metodo con `@Version` (`annulla`, `annullaDaNegozio`, `registraRitiro`, `registraRiconsegna`, `prorogaPrenotazione`, `aggiungiAccessori`, `trasferisciPrenotazione`) deve avere `catch (OptimisticLockingFailureException e)` → **409**.

Così nel progetto ci sono **tutti e due i lock**, ciascuno con un motivo:

| Dove | Lock | Perché |
|---|---|---|
| creare una prenotazione (online o al banco), prorogarla (insieme a `@Version`, [[#5.8 Proroga del noleggio\|5.8]]), aggiungere accessori in negozio ([[#5.2 Ritiro e pagamento\|5.2]]), trasferirla a un altro negozio ([[#5.9 Ritardo che coinvolge il cliente successivo\|5.9]]), diminuire la quantità di mezzi o accessori | **pessimistico** sul negozio | conflitti **frequenti** su una risorsa scarsa (l'ultimo mezzo), e il conflitto nasce da **righe nuove**, che un lock ottimistico non vede |
| annullare, ritirare e pagare, riconsegnare una prenotazione | **ottimistico** (`@Version`) + controllo dello stato | conflitti **rari** sulla **stessa riga** esistente; non conviene far aspettare nessuno. Al ritiro il pessimistico non serve perché non si assegnano bici precise ([[#5.2 Ritiro e pagamento\|5.2]]) |

Il `@Version` sulla prenotazione protegge anche le modifiche che arrivano "da un altro percorso" senza lock.

> [!WARNING] Ordine delle scritture su MySQL
> In `registraRitiro` e `registraRiconsegna` la prenotazione va aggiornata **per prima** (`saveAndFlush`), e solo dopo si inseriscono documento, penali e pagamento.
> - L'INSERT di una riga figlia mette un lock **condiviso** sulla prenotazione (controllo della foreign key). Se l'UPDATE della prenotazione arrivasse dopo, al commit, le due transazioni chiederebbero entrambe il lock **esclusivo** tenendo quello condiviso: **deadlock**, e il controller risponderebbe 500.
> - Con l'UPDATE per primo, la seconda transazione aspetta, poi trova la `version` cambiata e riceve l'eccezione ottimistica (409).
> - Verificato con il test `ConcorrenzaRitiroTest`: con l'ordine sbagliato MySQL segnala il deadlock già al primo tentativo.

### 7.3 Punto critico 3: codice sconto usato una volta sola
Lo stesso cliente potrebbe usare lo stesso codice in due prenotazioni contemporanee in **negozi diversi**, che hanno lock diversi: il lock sul negozio qui non aiuta.
- Il codice viene segnato come usato **nella stessa transazione** della prenotazione: se la prenotazione fallisce, il rollback lo rende di nuovo valido (con `rollbackFor`, vedi sopra).
Per risolvere questo problema si effettua un **aggiornamento condizionale**, cioè una `@Query` di update "segna usato **solo se** non è già usato" che restituisce quante righe ha cambiato. Se ha cambiato 0 righe → `CodiceScontoGiaUsatoException`.

**Stesso problema per la registrazione**: due richieste con la stessa email passano entrambe il controllo "email già usata?". Si risolve con un **vincolo di unicità** sull'email nel DB. Nel controller `catch (DataIntegrityViolationException e)` → `UtenteGiaRegistratoException`.

### 7.4 Operazioni di sola lettura
Ricerca negozi, calcolo della disponibilità per la mappa, proposte alternative, elenco prenotazioni e calendario usano `@Transactional(readOnly = true)`.

Sono **senza lock**, quindi il risultato può essere già "vecchio" quando l'utente lo guarda. È accettabile: la verifica vera si rifà con il lock al momento della prenotazione.

### 7.5 Isolamento
I vari tipi di isolamenti vengono sfruttati come segue:
- `READ_COMMITTED` + lock pessimistico sul negozio per le operazioni critiche ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]]).
- Isolamento di default + `@Version` per le modifiche alla singola prenotazione (annullare, ritirare, riconsegnare): basta il controllo sulla versione della riga.
- Default per le letture.

**Perché non `SERIALIZABLE`.** Gli appunti dicono di riservare l'isolamento **alto** (`SERIALIZABLE`) alle operazioni delicate *(appunti: [[Web#Transaction Isolation|Web – Transaction Isolation]])*, e la prenotazione è delicata. Non lo usiamo perché:
- il lock pessimistico serializza solo le operazioni **sullo stesso negozio**, mentre `SERIALIZABLE` avrebbe un costo su tutte le tabelle toccate e un comportamento che dipende dal DBMS;
- otteniamo lo stesso risultato ("uno dei due va a buon fine, l'altro no") con meno attese;
- è un compromesso consapevole tra consistenza e prestazioni: la frase "Buon ingegnere" degli appunti.

---

## 8. Implementazione delle parti

Convenzioni già decise:
- service con `@Transactional` (`readOnly = true` per le letture, `rollbackFor = Exception.class` per le scritture);
- **eccezioni checked** dedicate, lanciate dai service;
- **controller con `try/catch`** che le trasformano in `ResponseStatusException` con lo status giusto;
- prove con **Postman**;
- **MySQL anche in sviluppo** (non H2): le prove di concorrenza devono girare sul DB vero, perché lock e isolamento dipendono dal DBMS ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)|7.1]], [[#8.3 Prove con Postman|8.3]]).

### 8.1 Eccezioni previste

**Nostre (checked)**

| Eccezione | Quando | HTTP |
|---|---|---|
| `NegozioNonTrovatoException` | id negozio inesistente | 404 |
| `PrenotazioneNonTrovataException` | id prenotazione inesistente | 404 |
| `IntervalloNonValidoException` | fine prima dell'inizio, ritiro nel passato, orario non a step di 30 minuti | 400 |
| `NegozioChiusoException` | ritiro o riconsegna fuori orario | 400 |
| `OrarioNonValidoException` | orario di apertura con chiusura non dopo l'apertura ([[#5.10 Orari di apertura\|5.10]]) | 400 |
| `DisponibilitaInsufficienteException` | non ci sono abbastanza mezzi | 409 |
| `LimitePrenotazioniSuperatoException` | troppe prenotazioni attive o troppi mezzi | 409 |
| `StatoPrenotazioneNonValidoException` | es. pagare una prenotazione annullata o scaduta, ritiro fuori finestra | 409 |
| `CodiceScontoNonValidoException` | inesistente, scaduto, non ancora valido, di un altro cliente | 400 |
| `CodiceScontoGiaUsatoException` | già usato (anche per uso contemporaneo, [[#7.3 Punto critico 3: codice sconto usato una volta sola\|7.3]]) | 409 |
| `QuantitaMezziNonValidaException` | quantità negativa, o sotto il numero di mezzi fuori adesso ([[#5.6 Gestione dei mezzi\|5.6]]) | 409 |
| `AccessoNegozioNegatoException` | gestore che tocca un negozio non suo | 403 |
| `UtenteGiaRegistratoException` | email già usata | 409 |

**Di Spring (unchecked): vanno catturate a mano nei controller interessati**

| Eccezione | Quando | HTTP |
|---|---|---|
| `OptimisticLockingFailureException` | conflitto su `@Version` ([[#7.2 Punto critico 2: modifiche concorrenti alla stessa prenotazione\|7.2]]) | 409 |
| `PessimisticLockingFailureException` | lock non ottenuto in tempo o deadlock ([[#7.1 Punto critico 1: creare una prenotazione (online o al banco)\|7.1]]) | 503 |
| `DataIntegrityViolationException` | violato un vincolo di unicità (es. email) | 409 |

### 8.2 Endpoint per parte

| Parte | Endpoint | Service | Transazione e lock |
|---|---|---|---|
| Registrazione | `POST /api/clienti` | `registra` | `rollbackFor`, vincolo unico sull'email |
| Ricerca negozi sulla mappa | `GET /api/negozi/disponibili?lat=&lon=&raggioKm=&inizio=&fine=&richiesta=` | `cercaNegoziDisponibili` | `readOnly = true` |
| Proposte alternative | `GET /api/negozi/alternative?…` (stessi parametri) | `cercaAlternative` | `readOnly = true` |
| Dettaglio negozio, listini, accessori | `GET /api/negozi/{id}`, `GET /api/tipi-mezzo`, `GET /api/accessori` | letture | `readOnly = true` |
| Prenotazione online | `POST /api/prenotazioni` | `creaPrenotazione(cliente, richiesta)` | `READ_COMMITTED`, `rollbackFor` + **`PESSIMISTIC_WRITE` sul negozio** come prima query |
| Le mie prenotazioni | `GET /api/prenotazioni/mie?pageNumber=&pageSize=` | `prenotazioniCliente` | `readOnly = true`, paginata |
| Annullamento (cliente) | `POST /api/prenotazioni/{id}/annulla` | `annulla` | `rollbackFor`, **`@Version`** + controllo stato e scadenza |
| Elenco prenotazioni negozio | `GET /api/gestore/prenotazioni?data=&stato=&cerca=&pageNumber=&pageSize=` | `prenotazioniNegozio` | `readOnly = true`, solo il negozio del gestore |
| Prenotazione al banco | `POST /api/gestore/prenotazioni` | **stesso** `creaPrenotazione(cliente, richiesta)` | come la prenotazione online |
| Ritiro e pagamento | `POST /api/gestore/prenotazioni/{id}/ritiro` | `registraRitiro` | `rollbackFor`, **`@Version`**. Niente lock pessimistico: commento nel codice che spiega perché ([[#5.2 Ritiro e pagamento\|5.2]]) |
| Proroga | `POST /api/prenotazioni/{id}/proroga` (cliente) e `POST /api/gestore/prenotazioni/{id}/proroga` (gestore), body: nuovo orario di riconsegna | **stesso** `prorogaPrenotazione` | `READ_COMMITTED`, `rollbackFor` + **`PESSIMISTIC_WRITE` sul negozio** + **`@Version`** |
| Accessori aggiunti in negozio | `POST /api/gestore/prenotazioni/{id}/accessori` | `aggiungiAccessori` | `READ_COMMITTED`, `rollbackFor` + **`PESSIMISTIC_WRITE` sul negozio** + `@Version` |
| Trasferimento a un altro negozio | `POST /api/gestore/prenotazioni/{id}/trasferimento` (body: negozio di destinazione) | `trasferisciPrenotazione` | **una sola transazione**: `PESSIMISTIC_WRITE` sul negozio di destinazione + `@Version` sulla prenotazione originale ([[#5.9 Ritardo che coinvolge il cliente successivo\|5.9]]) |
| Riconsegna e penali | `POST /api/gestore/prenotazioni/{id}/riconsegna` | `registraRiconsegna` | `rollbackFor`, **`@Version`** |
| Annullamento (negozio) | `POST /api/gestore/prenotazioni/{id}/annulla` | `annullaDaNegozio` | `rollbackFor`, **`@Version`** |
| Calendario riconsegne | `GET /api/gestore/riconsegne?da=&a=` | `calendarioRiconsegne` | `readOnly = true` |
| Quantità di accessori nel negozio | `PATCH /api/gestore/accessori/{id}` (body: nuova quantità) | `cambiaQuantitaAccessori` | come per i mezzi |
| Festivi (admin) | `GET`/`POST`/`DELETE /api/admin/festivi` | `aggiungiFestivo`, `rimuoviFestivo` | `rollbackFor` |
| Orari di apertura (lettura) | `GET /api/negozi/{id}/orari` | `orariNegozio` | `readOnly = true` |
| Orari di apertura (admin) | `PUT`/`DELETE /api/admin/negozi/{id}/orari/{giorno}`, body della PUT: apertura e chiusura | `modificaOrario`, `rimuoviOrario` | `READ_COMMITTED`, `rollbackFor` + **`PESSIMISTIC_WRITE` sul negozio** ([[#5.10 Orari di apertura\|5.10]]) |
| Quantità di mezzi in servizio | `PATCH /api/gestore/mezzi/{tipo}` (body: nuova quantità) | `cambiaQuantitaMezzi` | `READ_COMMITTED`, `rollbackFor` + **`PESSIMISTIC_WRITE` sul negozio** |
| Promemoria un'ora prima | nessun endpoint: job `@Scheduled` | `inviaPromemoria` | una transazione breve per prenotazione, dopo l'invio della mail ([[#4.9 Promemoria via email un'ora prima del ritiro\|4.9]]) |
| Avviso ritardo al cliente successivo | nessun endpoint: stesso job `@Scheduled` | `gestisciRitardi` | prima si segna la prenotazione (transazione breve, `@Version`), poi si manda la mail ([[#5.9 Ritardo che coinvolge il cliente successivo\|5.9]]) |
| Prezzi (admin) | `GET`/`PUT /api/admin/listini/{tipo}`, `GET`/`PUT /api/admin/accessori/{id}`, `GET`/`PUT /api/admin/penali` | `aggiornaListino`, `aggiornaAccessorio`, `aggiornaPenali` | `rollbackFor`, `@Version` sui prezzi (due admin che modificano lo stesso prezzo) |

Gli URL del gestore non contengono l'id del negozio: il negozio si ricava dal gestore autenticato, così non si può nemmeno provare a chiedere quello di un altro.

>[!IMPORTANT] Note di implementazione
>- **Distanza.** Formula di Haversine con latitudine e longitudine del negozio. Si effettua con una `@Query(nativeQuery = true)` nel repository che calcola la distanza e ordina.Prima si filtra per distanza, poi si calcola la disponibilità solo sui negozi rimasti.
>- **Conteggio dei mezzi occupati.** Una `@Query` JPQL che somma le quantità delle prenotazioni del negozio che occupano mezzi (`PRENOTATA` non scaduta, `IN_CORSO` con la regola dei ritardi del [[#7. Database dinamico, concorrenza e transazioni|7]]), con intervallo sovrapposto, raggruppata per tipo di mezzo. **È la stessa query** per la mappa (senza lock) e per la prenotazione (dopo il lock).
>- **Parametro `richiesta`** nella GET (mezzi misti): ad esempio `richiesta=ELETTRICA:2,MUSCOLARE:1`. Se diventa scomodo si passa a una `POST /api/ricerche` con body JSON.
>- **Paginazione.** Parametri `pageNumber` e `pageSize` con default. L'ordinamento si sceglie tra pochi campi ammessi, non con un `sortBy` libero.
>- **Validazione** dei body con `@Valid` (email, telefono, quantità > 0).
>- **Dati personali nelle risposte.** Il documento d'identità e i dati del cliente non devono finire nelle risposte sbagliate ([[#5.2 Ritiro e pagamento|5.2]]), escludendo i campi dalla serializzazione.
>- **Email dopo il commit.** Il controller chiami il servizio mail **dopo** che `creaPrenotazione` è tornato senza errori, quindi dopo il commit.

### 8.3 Prove con Postman

**Ricerca negozi**
```
GET http://localhost:8080/api/negozi/disponibili?lat=38.11&lon=13.36&raggioKm=10&inizio=2026-12-01T09:00&fine=2026-12-01T13:00&richiesta=ELETTRICA:2,MUSCOLARE:1
Authorization: Bearer <token cliente>
→ 200 con la lista dei negozi (id, nome, distanza, lat/lon)
```

**Prenotazione**
```
POST http://localhost:8080/api/prenotazioni
Authorization: Bearer <token cliente>
Content-Type: application/json

{
  "negozioId": 3,
  "inizio": "2026-12-01T09:00",
  "fine": "2026-12-01T13:00",
  "mezzi": [ { "tipo": "ELETTRICA", "quantita": 2 }, { "tipo": "MUSCOLARE", "quantita": 1 } ],
  "codiceSconto": "SCONTO-AB12"
}
→ 201 con la prenotazione | 409 se non c'è disponibilità | 400 se il codice non è valido
```
Il cliente **non** è nel body: viene dal token. Non ci sono nemmeno gli accessori: li aggiunge il gestore al ritiro.

**Ritiro e pagamento (gestore)**
```
POST http://localhost:8080/api/gestore/prenotazioni/42/ritiro
Authorization: Bearer <token gestore>
Content-Type: application/json

{
  "documento": { "numero": "CA00000AA", "nome": "Mario", "cognome": "Rossi" },
  "metodoPagamento": "CARTA"
}
→ 200 | 403 se la prenotazione non è del negozio del gestore | 409 se lo stato non va bene, se è scaduta o se è stata modificata nel frattempo
```

**Provare la concorrenza**

> [!WARNING]
> Il **Collection Runner** di Postman esegue le richieste **una dopo l'altra**, non insieme: darebbe 201 e poi 409 anche **senza** lock, quindi non dimostra niente.

Modi che funzionano:
- **Pausa + due tab.** Solo in sviluppo, una pausa di qualche secondo dentro `creaPrenotazione` **dopo** aver preso il lock. Poi due tab di Postman lanciate a mano una subito dopo l'altra sull'ultimo mezzo: la seconda **aspetta** e poi risponde 409.
- **Due `curl`** lanciati in parallelo dal terminale.
- Un **test JUnit** con due thread.

---

## 9. Modello E-R

![[modello ER progetto web.svg]]

---

## 10. Priorità

La costruzione del progetto avviene secondo il seguente schema di priorità:

**Nucleo**: contiene tutte e quattro le parti richieste (T1, T2, C1, C2, [[#1. Idea in breve|1]])
- registrazione/login con i due ruoli;
- negozi, tipi di mezzo con le quantità per negozio, listini (dati iniziali);
- ricerca negozi con distanza e disponibilità;
- creazione prenotazione con lock pessimistico (online e al banco), annullamento, scadenza calcolata;
- elenco prenotazioni del gestore con ricerca e paginazione;
- ritiro + pagamento simulato, riconsegna con penali;
- test di concorrenza.

**Dopo**
- codici sconto ed email di conferma (anche solo nel log);
- promemoria un'ora prima del ritiro;
- proroga del noleggio ([[#5.8 Proroga del noleggio|5.8]]);
- endpoint dell'admin per i prezzi (all'inizio i prezzi arrivano dai dati iniziali);
- avviso al cliente successivo in caso di ritardo: accessorio in omaggio o proroga gratuita, oppure trasferimento a un altro negozio ([[#5.9 Ritardo che coinvolge il cliente successivo|5.9]]);
- accessori aggiunti in negozio, con quantità per negozio ([[#5.2 Ritiro e pagamento|5.2]]);
- calendario riconsegne;
- modifica delle quantità con elenco delle prenotazioni scoperte, annullamento da parte del negozio;
- proposte alternative: prima "primo orario utile nello stesso negozio", poi "negozio dove si libera prima".

**Se avanza tempo**
- mezzi per bambini ([[#4.2 Menu di ricerca|4.2]]);
- endpoint dell'admin per negozi e gestori;
- frontend con la mappa.

**Fuori dal progetto**
- pagamenti veri (carte, circuiti);
- riconsegna in un negozio diverso da quello di ritiro;
- recensioni, notifiche push, SMS;
- fatturazione e documenti fiscali;
- statistiche e grafici.
