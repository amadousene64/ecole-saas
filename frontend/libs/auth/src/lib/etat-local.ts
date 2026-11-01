/**
 * Ce que l'application garde dans le navigateur : stockages de session et locaux (école choisie,
 * adresse de retour, tout ce qu'un écran y aurait mis) et caches du service worker. Vidés à la
 * déconnexion : sur un téléphone partagé, la personne suivante ne doit trouver ni école, ni
 * enfant, ni réponse d'API gardée en cache.
 */

/** Vide les stockages de session et locaux de l'origine (synchrone). */
export function viderStockages(): void {
  for (const acces of [() => sessionStorage, () => localStorage]) {
    try {
      acces().clear();
    } catch {
      // Stockage inaccessible (navigation privée, politique du navigateur) : rien à vider.
    }
  }
}

/** Supprime tous les caches (réponses mises de côté par le service worker). */
export async function viderCachesServiceWorker(): Promise<void> {
  try {
    if (typeof caches === 'undefined') {
      return;
    }
    const noms = await caches.keys();
    await Promise.all(noms.map((nom) => caches.delete(nom)));
  } catch {
    // Contexte sans Cache Storage (http, navigation privée) : rien à vider.
  }
}

export async function viderEtatLocal(): Promise<void> {
  viderStockages();
  await viderCachesServiceWorker();
}
