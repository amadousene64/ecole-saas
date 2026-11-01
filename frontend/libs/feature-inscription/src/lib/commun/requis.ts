/** Les DTO générés déclarent tout optionnel ; un identifiant absent est une anomalie du serveur. */
export function requis<T>(valeur: T | undefined | null): T {
  if (valeur === undefined || valeur === null) {
    throw new Error('Réponse du serveur incomplète (identifiant manquant).');
  }
  return valeur;
}
