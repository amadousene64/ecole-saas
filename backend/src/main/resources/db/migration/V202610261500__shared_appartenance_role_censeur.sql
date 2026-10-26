-- Ajout du rôle CENSEUR (lycée). La contrainte liste les rôles autorisés : on la recrée avec le
-- nouveau rôle (la migration V202610261101 d'origine ne se modifie pas).
ALTER TABLE appartenance DROP CONSTRAINT ck_appartenance_role;
ALTER TABLE appartenance ADD CONSTRAINT ck_appartenance_role CHECK (role IN
  ('DIRECTEUR', 'CENSEUR', 'SECRETARIAT', 'SURVEILLANT', 'ENSEIGNANT', 'COMPTABLE', 'PARENT', 'ELEVE'));
