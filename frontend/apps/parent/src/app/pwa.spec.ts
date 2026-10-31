import config from '../../ngsw-config.json';

/**
 * Exigence : aucune réponse de l'API (/api/**) ne doit se trouver dans le cache du service worker.
 * Ce sont des données d'enfants : sur un téléphone partagé, un cache survivrait à la déconnexion.
 *
 * Le service worker d'Angular ne met une réponse HTTP en cache que par un `dataGroups` ou par un
 * `assetGroups` dont un motif la désigne. On prouve donc : (1) aucun `dataGroups`, (2) aucun motif
 * d'`assetGroups` ne désigne une adresse de l'API, (3) l'API est exclue de la navigation servie
 * par `index.html`.
 */

interface Groupe {
  name: string;
  resources?: { files?: string[]; urls?: string[] };
}

const ADRESSES_API = [
  '/api/v1/moi/enfants',
  '/api/v1/moi/eleve',
  '/api/v1/eleves/abc/pieces',
  '/api/v1/activations',
];

/** Motifs du service worker Angular : `**` toute suite, `*` sans barre, `?` un caractère. */
function enExpression(motif: string): RegExp {
  const source = motif
    .replace(/[.+^${}()|[\]\\]/g, '\\$&')
    .replace(/\*\*/g, '\u0000')
    .replace(/\*/g, '[^/]*')
    .replace(/\?/g, '[^/]')
    .replace(/\u0000/g, '.*');
  return new RegExp(`^${source}$`);
}

describe('service worker : aucune réponse d’API en cache', () => {
  const conf = config as unknown as {
    dataGroups?: unknown[];
    assetGroups: Groupe[];
    navigationUrls: string[];
  };

  it('ne déclare aucun dataGroups', () => {
    expect(conf.dataGroups ?? []).toEqual([]);
  });

  it('aucun groupe de ressources ne désigne une adresse de l’API', () => {
    const motifs = conf.assetGroups.flatMap((g) => [
      ...(g.resources?.files ?? []),
      ...(g.resources?.urls ?? []),
    ]);
    expect(motifs.length).toBeGreaterThan(0);
    for (const motif of motifs.filter((m) => !m.startsWith('!'))) {
      const expression = enExpression(motif);
      for (const adresse of ADRESSES_API) {
        expect(expression.test(adresse), `${motif} désigne ${adresse}`).toBe(
          false,
        );
      }
    }
    // Aucun motif ne vise un hôte externe non plus (pas de cache de réponses de tiers).
    expect(motifs.some((m) => /^https?:/.test(m))).toBe(false);
  });

  it('exclut l’API de la navigation servie par index.html', () => {
    expect(conf.navigationUrls).toContain('!/api/**');
    const exclusions = conf.navigationUrls
      .filter((m) => m.startsWith('!'))
      .map((m) => enExpression(m.slice(1)));
    for (const adresse of ADRESSES_API) {
      expect(exclusions.some((e) => e.test(adresse))).toBe(true);
    }
  });
});
