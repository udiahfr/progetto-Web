-- Schema del database del noleggio (MySQL).
-- Si esegue dalla cartella java/Noleggio:
--   mysql -u root -p < src/main/resources/other/database_mysql.sql
-- Cancella e ricrea tutto da zero: i dati presenti vanno persi.
-- Le @Entity devono corrispondere a queste tabelle: all'avvio Hibernate
-- le controlla (ddl-auto: validate) e, se non corrispondono, non parte.

DROP SCHEMA IF EXISTS noleggio;
CREATE SCHEMA noleggio DEFAULT CHARACTER SET utf8mb4;
USE noleggio;

-- Ordine delle tabelle: prima quelle referenziate, poi quelle con le chiavi esterne.

-- Negozio: chiave numerica auto-increment; il nome (chiave nel modello E-R) resta unico.
CREATE TABLE negozio (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome        VARCHAR(100) NOT NULL,
    indirizzo   VARCHAR(200) NOT NULL,
    latitudine  DOUBLE       NOT NULL,
    longitudine DOUBLE       NOT NULL,
    CONSTRAINT uq_negozio_nome UNIQUE (nome),
    CONSTRAINT ck_negozio_latitudine  CHECK (latitudine  BETWEEN -90  AND 90),
    CONSTRAINT ck_negozio_longitudine CHECK (longitudine BETWEEN -180 AND 180)
);

-- Orario di apertura (R1): una fascia continuata per giorno della settimana.
-- Un giorno senza riga = negozio chiuso quel giorno.
-- giorno: i valori di java.time.DayOfWeek (@Enumerated(EnumType.STRING)).
-- Chiave id invece della chiave composta (negozio, giorno): la coppia resta unica.
CREATE TABLE orario (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    negozio_id BIGINT NOT NULL,
    giorno     ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') NOT NULL,
    apertura   TIME   NOT NULL,
    chiusura   TIME   NOT NULL,
    CONSTRAINT fk_orario_negozio FOREIGN KEY (negozio_id) REFERENCES negozio (id),
    CONSTRAINT uq_orario_negozio_giorno UNIQUE (negozio_id, giorno),
    CONSTRAINT ck_orario_intervallo CHECK (apertura < chiusura)
);

-- Tipo di mezzo con il suo listino, uguale in tutti i negozi.
-- I tipi sono dati, non codice: se ne aggiunge uno con un INSERT.
-- 4 prezzi: orario/giornaliero x feriale/festivo (festivo = fine settimana e festivi).
CREATE TABLE tipo_mezzo (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome                        VARCHAR(100) NOT NULL,
    prezzo_orario_feriale       DECIMAL(8,2) NOT NULL,
    prezzo_orario_festivo       DECIMAL(8,2) NOT NULL,
    prezzo_giornaliero_feriale  DECIMAL(8,2) NOT NULL,
    prezzo_giornaliero_festivo  DECIMAL(8,2) NOT NULL,
    CONSTRAINT uq_tipo_mezzo_nome UNIQUE (nome),
    CONSTRAINT ck_tipo_mezzo_prezzi CHECK (
        prezzo_orario_feriale >= 0 AND prezzo_orario_festivo >= 0 AND
        prezzo_giornaliero_feriale >= 0 AND prezzo_giornaliero_festivo >= 0)
);

-- Mezzi del negozio (R5): quanti mezzi di un tipo il negozio ha in servizio.
-- La relazione ha un attributo (quantita), quindi è una tabella con chiave propria
-- e non un @ManyToMany. Nessuna riga = il negozio non ha quel tipo.
-- La quantità si modifica sotto il lock pessimistico sul negozio.
CREATE TABLE mezzi_in_negozio (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    negozio_id    BIGINT NOT NULL,
    tipo_mezzo_id BIGINT NOT NULL,
    quantita      INT    NOT NULL,
    CONSTRAINT fk_mezzi_in_negozio_negozio FOREIGN KEY (negozio_id)    REFERENCES negozio (id),
    CONSTRAINT fk_mezzi_in_negozio_tipo_mezzo FOREIGN KEY (tipo_mezzo_id) REFERENCES tipo_mezzo (id),
    CONSTRAINT uq_mezzi_in_negozio UNIQUE (negozio_id, tipo_mezzo_id),
    CONSTRAINT ck_mezzi_in_negozio_quantita CHECK (quantita >= 0)
);

-- Utente: la gerarchia Cliente/Gestore/Admin (totale ed esclusiva) è in un'unica
-- tabella (JPA @Inheritance SINGLE_TABLE): ruolo è il discriminatore, che Hibernate
-- legge per creare la sottoclasse giusta. Le figlie non hanno attributi propri:
-- l'unica differenza è il negozio del gestore (R2), presente solo per i gestori.
-- Il legame con l'account Keycloak è l'email.
CREATE TABLE utente (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    ruolo      VARCHAR(20)  NOT NULL,
    nome       VARCHAR(50)  NOT NULL,
    cognome    VARCHAR(50)  NOT NULL,
    email      VARCHAR(100) NOT NULL,
    telefono   VARCHAR(20)  NOT NULL,
    negozio_id BIGINT NULL,
    CONSTRAINT fk_utente_negozio FOREIGN KEY (negozio_id) REFERENCES negozio (id),
    CONSTRAINT uq_utente_email UNIQUE (email),
    CONSTRAINT ck_utente_ruolo CHECK (ruolo IN ('CLIENTE','GESTORE','ADMIN')),
    -- un gestore lavora in un negozio; clienti e admin non hanno negozio
    CONSTRAINT ck_utente_negozio CHECK (
        (ruolo = 'GESTORE' AND negozio_id IS NOT NULL) OR
        (ruolo <> 'GESTORE' AND negozio_id IS NULL))
);

-- Festivi della catena (uguali per tutti i negozi), gestiti dall'admin.
-- Una riga per data (anno per anno). Sabato e domenica non si inseriscono:
-- il codice li riconosce dal giorno della settimana.
CREATE TABLE festivo (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    data DATE NOT NULL,
    CONSTRAINT uq_festivo_data UNIQUE (data)
);

-- Prenotazione. inizio/fine = ritiro e riconsegna previsti (la proroga sposta fine);
-- ritardo_effettivo/riconsegna_effettiva servono per le penali di ritardo.
-- coinvolta_ritardo (per ora non usato): avvisata perché un cliente precedente
-- è in ritardo (compenso al ritiro).
-- prenotazione_originale_id (R18): la prenotazione annullata che questa sostituisce
-- (trasferimento in un altro negozio). version: lock ottimistico (@Version).
CREATE TABLE prenotazione (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    cliente_id                BIGINT   NOT NULL,
    negozio_id                BIGINT   NOT NULL,
    stato                     ENUM('PRENOTATA','IN_CORSO','CONCLUSA','ANNULLATA','SCADUTA') NOT NULL,
    motivo_annullamento       ENUM('CLIENTE','NEGOZIO','TRASFERIMENTO') NULL,
    inizio                    DATETIME NOT NULL,
    fine                      DATETIME NOT NULL,
    ritardo_effettivo         DATETIME NULL,
    riconsegna_effettiva      DATETIME NULL,
    -- coinvolta_ritardo      BOOLEAN  NOT NULL DEFAULT FALSE,
    prenotazione_originale_id BIGINT   NULL,
    version                   BIGINT   NOT NULL DEFAULT 0,
    CONSTRAINT fk_prenotazione_cliente   FOREIGN KEY (cliente_id) REFERENCES utente (id),
    CONSTRAINT fk_prenotazione_negozio   FOREIGN KEY (negozio_id) REFERENCES negozio (id),
    CONSTRAINT fk_prenotazione_originale FOREIGN KEY (prenotazione_originale_id) REFERENCES prenotazione (id),
    CONSTRAINT uq_prenotazione_originale UNIQUE (prenotazione_originale_id),
    CONSTRAINT ck_prenotazione_intervallo CHECK (inizio < fine),
    CONSTRAINT ck_prenotazione_motivo CHECK (
        (stato = 'ANNULLATA' AND motivo_annullamento IS NOT NULL) OR
        (stato <> 'ANNULLATA' AND motivo_annullamento IS NULL)),
    INDEX idx_prenotazione_disponibilita (negozio_id, stato, inizio, fine)
);

-- Mezzi prenotati (R8): quanti mezzi di un tipo e il prezzo di uno di essi per
-- tutto il periodo, fissato al momento della prenotazione.
CREATE TABLE mezzi_in_prenotazione (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    prenotazione_id BIGINT       NOT NULL,
    tipo_mezzo_id   BIGINT       NOT NULL,
    quantita        INT          NOT NULL,
    prezzo_unitario DECIMAL(8,2) NOT NULL,
    CONSTRAINT fk_mezzi_in_prenotazione_prenotazione FOREIGN KEY (prenotazione_id) REFERENCES prenotazione (id),
    CONSTRAINT fk_mezzi_in_prenotazione_tipo_mezzo FOREIGN KEY (tipo_mezzo_id)   REFERENCES tipo_mezzo (id),
    CONSTRAINT uq_mezzi_in_prenotazione UNIQUE (prenotazione_id, tipo_mezzo_id),
    CONSTRAINT ck_mezzi_in_prenotazione_quantita CHECK (quantita > 0),
    CONSTRAINT ck_mezzi_in_prenotazione_prezzo CHECK (prezzo_unitario >= 0)
);

-- Documento d'identità lasciato al ritiro (R16). Prenotazione 0:1 - Documento 1:1:
-- la FK sta qui (obbligatoria) e UNIQUE impedisce due documenti per la stessa prenotazione.
-- codice: solo lettere maiuscole e cifre (documenti anche non italiani); non è unico,
-- perché lo stesso cliente può lasciare la stessa carta in prenotazioni diverse.
CREATE TABLE documento (
    codice          VARCHAR(20) PRIMARY KEY,
    prenotazione_id BIGINT      NOT NULL,
    nome            VARCHAR(50) NOT NULL,
    cognome         VARCHAR(50) NOT NULL,
    CONSTRAINT fk_documento_prenotazione FOREIGN KEY (prenotazione_id) REFERENCES prenotazione (id),
    CONSTRAINT uq_documento_prenotazione UNIQUE (prenotazione_id)
);

-- Pagamento simulato (R12, R13), registrato dal gestore che incassa.
-- Al massimo uno al RITIRO (noleggio + accessori + penale di ritardo al ritiro - sconto)
-- e uno alla RICONSEGNA (penali + eventuale proroga). Niente pagamenti da zero.
CREATE TABLE pagamento (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    prenotazione_id BIGINT       NOT NULL,
    gestore_id      BIGINT       NOT NULL,
    causale         ENUM('RITIRO','RICONSEGNA') NOT NULL,
    metodo          ENUM('CONTANTI','CARTA')    NOT NULL,
    importo         DECIMAL(8,2) NOT NULL,
    data            DATETIME     NOT NULL,
    CONSTRAINT fk_pagamento_prenotazione FOREIGN KEY (prenotazione_id) REFERENCES prenotazione (id),
    CONSTRAINT fk_pagamento_gestore      FOREIGN KEY (gestore_id)      REFERENCES utente (id),
    CONSTRAINT uq_pagamento_prenotazione_causale UNIQUE (prenotazione_id, causale),
    CONSTRAINT ck_pagamento_importo CHECK (importo > 0)
);
