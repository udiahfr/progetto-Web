-- Schema del database del noleggio (MySQL).
-- Si esegue da terminale:  mysql -u root -p < "<absolute_path>"
-- Cancella e ricrea tutto da zero: i dati presenti vanno persi.

DROP SCHEMA IF EXISTS noleggio;
CREATE SCHEMA noleggio;
USE noleggio;

-- Tabelle: generate automaticamente da Hibernate

