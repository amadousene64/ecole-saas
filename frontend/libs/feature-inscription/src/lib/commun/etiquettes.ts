import { TonStatut } from '@ecole/ui';
import { StatutInscription } from '../services/service-eleves';
import { TypePiece } from '../services/service-dossier';

export const STATUTS_INSCRIPTION: {
  valeur: StatutInscription;
  libelle: string;
  ton: TonStatut;
}[] = [
  { valeur: 'PRE_INSCRIT', libelle: 'Pré-inscrit', ton: 'safran' },
  { valeur: 'INSCRIT', libelle: 'Inscrit', ton: 'primaire' },
  { valeur: 'TRANSFERE', libelle: 'Transféré', ton: 'neutre' },
  { valeur: 'ABANDON', libelle: 'Abandon', ton: 'neutre' },
  { valeur: 'RADIE', libelle: 'Radié', ton: 'bissap' },
];

export function libelleStatut(statut: StatutInscription | undefined): string {
  return STATUTS_INSCRIPTION.find((s) => s.valeur === statut)?.libelle ?? '';
}

export function tonStatut(statut: StatutInscription | undefined): TonStatut {
  return STATUTS_INSCRIPTION.find((s) => s.valeur === statut)?.ton ?? 'neutre';
}

export const LIBELLES_PIECE: Record<TypePiece, string> = {
  ACTE_NAISSANCE: 'Acte de naissance',
  CERTIFICAT_SCOLARITE: 'Certificat de scolarité',
  CERTIFICAT_MEDICAL: 'Certificat médical',
  PHOTO_IDENTITE: "Photo d'identité",
};

export function libellePiece(type: string | undefined): string {
  return LIBELLES_PIECE[type as TypePiece] ?? type ?? '';
}

/** « Analyse en cours » côté interface = EN_ANALYSE ; « vérifiée » = SAIN ; « refusée » = INFECTE. */
export const LIBELLES_ANALYSE = {
  EN_ANALYSE: { libelle: 'Analyse en cours', ton: 'safran' },
  SAIN: { libelle: 'Vérifiée', ton: 'primaire' },
  INFECTE: { libelle: 'Refusée', ton: 'bissap' },
} as const satisfies Record<string, { libelle: string; ton: TonStatut }>;

export const TYPES_LIEN = [
  { valeur: 'PERE', libelle: 'Père' },
  { valeur: 'MERE', libelle: 'Mère' },
  { valeur: 'TUTEUR', libelle: 'Tuteur' },
  { valeur: 'AUTRE', libelle: 'Autre' },
] as const;

export function libelleLien(type: string | undefined): string {
  return TYPES_LIEN.find((t) => t.valeur === type)?.libelle ?? type ?? '';
}
