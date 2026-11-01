import { EtablissementDto } from '@ecole/api-client';

export type StatutEtablissement = NonNullable<EtablissementDto['statut']>;
export type TypePeriode = 'TRIMESTRE' | 'SEMESTRE';
export type ModeNotation = 'BAREME' | 'COEFFICIENT';
export type StatutAnnee = 'PREPARATION' | 'EN_COURS' | 'CLOTUREE';
export type TypeEvenement = 'FERIE' | 'VACANCES';

export const STATUTS_ETABLISSEMENT: {
  valeur: StatutEtablissement;
  libelle: string;
}[] = [
  { valeur: 'PUBLIC', libelle: 'Public' },
  { valeur: 'PRIVE_LAIQUE', libelle: 'Privé laïc' },
  { valeur: 'PRIVE_CONFESSIONNEL', libelle: 'Privé confessionnel' },
];

export const TYPES_PERIODE: { valeur: TypePeriode; libelle: string }[] = [
  { valeur: 'TRIMESTRE', libelle: 'Trimestres' },
  { valeur: 'SEMESTRE', libelle: 'Semestres' },
];

export const MODES_NOTATION: { valeur: ModeNotation; libelle: string }[] = [
  { valeur: 'BAREME', libelle: 'Barème par discipline' },
  { valeur: 'COEFFICIENT', libelle: 'Coefficients' },
];

export const LIBELLES_STATUT_ANNEE: Record<StatutAnnee, string> = {
  PREPARATION: 'En préparation',
  EN_COURS: 'En cours',
  CLOTUREE: 'Clôturée',
};

export const LIBELLES_CYCLE: Record<string, string> = {
  ELEMENTAIRE: 'Élémentaire',
  MOYEN: 'Moyen',
  SECONDAIRE: 'Secondaire',
};

export const TYPES_EVENEMENT: { valeur: TypeEvenement; libelle: string }[] = [
  { valeur: 'FERIE', libelle: 'Jour férié' },
  { valeur: 'VACANCES', libelle: 'Vacances' },
];

/** Règle du modèle : la base 10 n'existe qu'avec les barèmes (les coefficients sont sur 20). */
export function baseCompatible(
  mode: ModeNotation,
  base: number | null,
): boolean {
  return !(mode === 'COEFFICIENT' && base === 10);
}
