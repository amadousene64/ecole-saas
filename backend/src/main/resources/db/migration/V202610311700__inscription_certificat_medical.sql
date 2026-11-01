-- Module inscription : le type CERTIFICAT est scindé. Le certificat médical est une donnée de
-- santé (lecture par DIRECTEUR et CENSEUR seulement, journalisée) ; le certificat de scolarité
-- ne l'est pas. Faute de savoir lequel était déposé, une pièce CERTIFICAT existante devient
-- CERTIFICAT_MEDICAL : la règle la plus protectrice.

ALTER TABLE piece_dossier DROP CONSTRAINT ck_piece_dossier_type;
-- CERTIFICAT_SCOLARITE remplirait exactement VARCHAR(20) : marge pour de futurs types.
ALTER TABLE piece_dossier ALTER COLUMN type TYPE VARCHAR(30);

-- La RLS forcée cacherait les lignes au propriétaire (aucune école courante) : levée le temps de
-- la reprise, dans la transaction de la migration.
ALTER TABLE piece_dossier NO FORCE ROW LEVEL SECURITY;
UPDATE piece_dossier SET type = 'CERTIFICAT_MEDICAL' WHERE type = 'CERTIFICAT';
ALTER TABLE piece_dossier FORCE ROW LEVEL SECURITY;

ALTER TABLE piece_dossier ADD CONSTRAINT ck_piece_dossier_type
  CHECK (type IN ('ACTE_NAISSANCE', 'CERTIFICAT_SCOLARITE', 'CERTIFICAT_MEDICAL', 'PHOTO_IDENTITE'));
