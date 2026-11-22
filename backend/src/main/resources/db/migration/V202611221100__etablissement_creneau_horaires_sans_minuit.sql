-- Un créneau ne passe pas minuit : « TIME + INTERVAL » repartait à zéro (23:50 + 15 min = 00:05),
-- si bien que 23:50 → 08:00 respectait l'ancienne contrainte. « fin - debut » est un intervalle
-- qui ne boucle pas.
ALTER TABLE creneau DROP CONSTRAINT ck_creneau_heures;
ALTER TABLE creneau ADD CONSTRAINT ck_creneau_heures CHECK (
  debut >= TIME '06:00' AND fin <= TIME '20:00'
  AND debut < fin AND fin - debut >= INTERVAL '15 minutes');
