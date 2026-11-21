-- Schema del database del noleggio (MySQL).
-- Si esegue dalla cartella java/Noleggio:
--   mysql -u root -p < src/main/resources/other/database_mysql.sql
-- Cancella e ricrea tutto da zero: i dati presenti vanno persi.

DROP SCHEMA IF EXISTS noleggio;
CREATE SCHEMA noleggio;
USE noleggio;

-- Tabelle: generate automaticamente da Hibernate

