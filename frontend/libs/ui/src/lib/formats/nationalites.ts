/**
 * Nationalités proposées dans les listes (maquettes : « Sénégalaise », jamais « SN »). La valeur
 * enregistrée reste le code pays ISO 3166-1 alpha-2 attendu par l'API. Sénégalaise en tête, puis
 * l'ordre alphabétique.
 */
export interface Nationalite {
  readonly code: string;
  readonly libelle: string;
}

const AUTRES: readonly Nationalite[] = [
  { code: 'DZ', libelle: 'Algérienne' },
  { code: 'DE', libelle: 'Allemande' },
  { code: 'US', libelle: 'Américaine' },
  { code: 'BE', libelle: 'Belge' },
  { code: 'BJ', libelle: 'Béninoise' },
  { code: 'GW', libelle: 'Bissau-guinéenne' },
  { code: 'GB', libelle: 'Britannique' },
  { code: 'BF', libelle: 'Burkinabè' },
  { code: 'CM', libelle: 'Camerounaise' },
  { code: 'CA', libelle: 'Canadienne' },
  { code: 'CV', libelle: 'Cap-verdienne' },
  { code: 'CN', libelle: 'Chinoise' },
  { code: 'CG', libelle: 'Congolaise (Congo)' },
  { code: 'CD', libelle: 'Congolaise (RD Congo)' },
  { code: 'ES', libelle: 'Espagnole' },
  { code: 'FR', libelle: 'Française' },
  { code: 'GA', libelle: 'Gabonaise' },
  { code: 'GM', libelle: 'Gambienne' },
  { code: 'GH', libelle: 'Ghanéenne' },
  { code: 'GN', libelle: 'Guinéenne' },
  { code: 'IT', libelle: 'Italienne' },
  { code: 'CI', libelle: 'Ivoirienne' },
  { code: 'LB', libelle: 'Libanaise' },
  { code: 'LR', libelle: 'Libérienne' },
  { code: 'ML', libelle: 'Malienne' },
  { code: 'MA', libelle: 'Marocaine' },
  { code: 'MR', libelle: 'Mauritanienne' },
  { code: 'NE', libelle: 'Nigérienne' },
  { code: 'NG', libelle: 'Nigériane' },
  { code: 'PT', libelle: 'Portugaise' },
  { code: 'SL', libelle: 'Sierra-léonaise' },
  { code: 'CH', libelle: 'Suisse' },
  { code: 'TG', libelle: 'Togolaise' },
  { code: 'TN', libelle: 'Tunisienne' },
];

export const NATIONALITES: readonly Nationalite[] = [
  { code: 'SN', libelle: 'Sénégalaise' },
  ...AUTRES,
];

/**
 * Options d'une liste de nationalités : la liste de référence, plus le code déjà enregistré s'il
 * n'y figure pas (un élève importé ne perd pas sa nationalité en ouvrant sa fiche).
 */
export function optionsNationalite(
  codeActuel: string | null | undefined,
): readonly Nationalite[] {
  if (!codeActuel || NATIONALITES.some((n) => n.code === codeActuel)) {
    return NATIONALITES;
  }
  return [...NATIONALITES, { code: codeActuel, libelle: codeActuel }];
}
