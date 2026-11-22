-- Dati iniziali del noleggio (dati di prova inventati).
-- Si esegue DOPO database_mysql.sql, dalla cartella java/Noleggio:
--   mysql -u root -p < src/main/resources/other/dati_iniziali.sql
-- Gli id li assegna l'auto-increment: nelle chiavi esterne si usano sottoquery
-- sul nome (o sull'email), così non serve conoscere i numeri.
-- Prenotazioni e pagamenti non ci sono: si creano provando l'app.

USE noleggio;

-- Negozi (Palermo). Coordinate in gradi decimali: latitudine ~38, longitudine ~13.
INSERT INTO negozio (nome, indirizzo, latitudine, longitudine) VALUES
    ('Centro',    'Via Maqueda 100, Palermo',        38.1157, 13.3615),
    ('Politeama', 'Piazza Ruggero Settimo 1, Palermo', 38.1230, 13.3540),
    ('Mondello',  'Viale Regina Elena 50, Palermo',   38.1960, 13.3270);

-- Orari: Centro e Politeama chiusi la domenica; Mondello aperto tutti i giorni.
INSERT INTO orario (negozio_id, giorno, apertura, chiusura)
SELECT n.id, g.giorno, '09:00', '19:00'
FROM negozio n
JOIN (SELECT 'MONDAY' AS giorno UNION ALL SELECT 'TUESDAY' UNION ALL SELECT 'WEDNESDAY'
      UNION ALL SELECT 'THURSDAY' UNION ALL SELECT 'FRIDAY' UNION ALL SELECT 'SATURDAY') g
WHERE n.nome IN ('Centro', 'Politeama');

INSERT INTO orario (negozio_id, giorno, apertura, chiusura)
SELECT n.id, g.giorno, '08:30', '20:00'
FROM negozio n
JOIN (SELECT 'MONDAY' AS giorno UNION ALL SELECT 'TUESDAY' UNION ALL SELECT 'WEDNESDAY'
      UNION ALL SELECT 'THURSDAY' UNION ALL SELECT 'FRIDAY' UNION ALL SELECT 'SATURDAY'
      UNION ALL SELECT 'SUNDAY') g
WHERE n.nome = 'Mondello';

-- Tipi di mezzo con il listino (orario e giornaliero, feriale e festivo).
INSERT INTO tipo_mezzo (nome, prezzo_orario_feriale, prezzo_orario_festivo,
                        prezzo_giornaliero_feriale, prezzo_giornaliero_festivo) VALUES
    ('Bici muscolare', 4.00,  5.00, 15.00, 18.00),
    ('Bici elettrica', 8.00, 10.00, 35.00, 40.00),
    ('Monopattino',    6.00,  7.00, 25.00, 30.00),
    ('Bici bambino',   3.00,  4.00, 12.00, 14.00);

-- Mezzi in servizio per negozio.
-- Casi utili per le prove: Politeama ha UNA sola bici elettrica (concorrenza
-- sull'ultimo mezzo); Centro non ha bici per bambini (nessuna riga = tipo assente).
INSERT INTO mezzi_in_negozio (negozio_id, tipo_mezzo_id, quantita)
SELECT n.id, t.id, q.quantita
FROM (SELECT 'Centro' AS negozio, 'Bici muscolare' AS tipo, 10 AS quantita
      UNION ALL SELECT 'Centro',    'Bici elettrica', 6
      UNION ALL SELECT 'Centro',    'Monopattino',    4
      UNION ALL SELECT 'Politeama', 'Bici muscolare', 5
      UNION ALL SELECT 'Politeama', 'Bici elettrica', 1
      UNION ALL SELECT 'Politeama', 'Monopattino',    3
      UNION ALL SELECT 'Politeama', 'Bici bambino',   2
      UNION ALL SELECT 'Mondello',  'Bici muscolare', 12
      UNION ALL SELECT 'Mondello',  'Bici elettrica', 8
      UNION ALL SELECT 'Mondello',  'Monopattino',    5
      UNION ALL SELECT 'Mondello',  'Bici bambino',   4) q
JOIN negozio n    ON n.nome = q.negozio
JOIN tipo_mezzo t ON t.nome = q.tipo;

-- Utenti: un admin e un gestore per negozio (due per Centro, per provare
-- due gestori dello stesso negozio). Le email devono essere le stesse degli
-- utenti creati in Keycloak. I clienti si registrano da soli.
INSERT INTO utente (ruolo, nome, cognome, email, telefono, negozio_id) VALUES
    ('ADMIN', 'Anna', 'Amministratrice', 'admin@noleggio.test', '3330000000', NULL);

INSERT INTO utente (ruolo, nome, cognome, email, telefono, negozio_id)
SELECT 'GESTORE', g.nome, g.cognome, g.email, g.telefono, n.id
FROM (SELECT 'Centro' AS negozio, 'Giulia' AS nome, 'Rossi' AS cognome,
             'gestore.centro@noleggio.test' AS email, '3330000001' AS telefono
      UNION ALL SELECT 'Centro',    'Marco', 'Bianchi', 'gestore2.centro@noleggio.test',  '3330000002'
      UNION ALL SELECT 'Politeama', 'Sara',  'Verdi',   'gestore.politeama@noleggio.test', '3330000003'
      UNION ALL SELECT 'Mondello',  'Luca',  'Neri',    'gestore.mondello@noleggio.test',  '3330000004') g
JOIN negozio n ON n.nome = g.negozio;

-- Festivi nazionali 2026-2027 (sabato e domenica non servono: li riconosce il codice,
-- ma le date che cadono nel weekend restano per semplicità). Pasquetta: 6/4/2026 e 29/3/2027.
-- 15 luglio: Santa Rosalia, patrona di Palermo (festivo solo locale).
INSERT INTO festivo (data) VALUES
    ('2026-01-01'), ('2026-01-06'), ('2026-04-06'), ('2026-04-25'), ('2026-05-01'),
    ('2026-06-02'), ('2026-07-15'), ('2026-08-15'), ('2026-11-01'), ('2026-12-08'),
    ('2026-12-25'), ('2026-12-26'),
    ('2027-01-01'), ('2027-01-06'), ('2027-03-29'), ('2027-04-25'), ('2027-05-01'),
    ('2027-06-02'), ('2027-07-15'), ('2027-08-15'), ('2027-11-01'), ('2027-12-08'),
    ('2027-12-25'), ('2027-12-26');
