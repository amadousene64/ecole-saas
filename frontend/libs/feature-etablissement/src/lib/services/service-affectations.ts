import { inject, Service } from '@angular/core';
import {
  AffectationDto,
  AffectationRequete,
  Api,
  MembreDto,
  affectationAffectables,
  affectationChangerPeriode,
  affectationCreer,
  affectationLister,
  affectationTerminer,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';
import { requis } from '../commun/requis';

/** Affectations du personnel (direction, censeur) et annuaire des affectables. */
@Service()
export class ServiceAffectations {
  private readonly api = inject(Api);

  /** Affectations de l'année, en cours et terminées. */
  lister(anneeId: string): Promise<AffectationDto[]> {
    return this.api.invoke(affectationLister, { annee: anneeId });
  }

  /** Une affectation par cycle pour une surveillance sur plusieurs cycles. */
  creer(corps: AffectationRequete): Promise<AffectationDto[]> {
    return this.api.invoke(affectationCreer, { body: corps });
  }

  changerPeriode(
    affectation: AffectationDto,
    debut: string,
    fin: string | undefined,
  ): Promise<void> {
    return this.api.invoke(affectationChangerPeriode, {
      id: requis(affectation.id),
      'If-Match': ifMatch(affectation.version),
      body: { debut, fin },
    });
  }

  /** Fin aujourd'hui (effet immédiat) ; annulée si elle n'avait pas commencé. */
  terminer(affectation: AffectationDto): Promise<void> {
    return this.api.invoke(affectationTerminer, {
      id: requis(affectation.id),
      'If-Match': ifMatch(affectation.version),
      body: {},
    });
  }

  affectables(): Promise<MembreDto[]> {
    return this.api.invoke(affectationAffectables, {});
  }
}

/**
 * « DIOP Awa · …1f2e », ou « Compte jamais connecté · …1f2e ». La fin de l'identifiant distingue
 * deux homonymes : le nom vient du compte, que son titulaire peut modifier.
 */
export function nomMembre(m: MembreDto | undefined, id?: string): string {
  const ident = (m?.utilisateurId ?? id ?? '').slice(-4);
  if (m?.nom || m?.prenoms) {
    const nom = [m.nom?.toUpperCase(), m.prenoms].filter(Boolean).join(' ');
    return `${nom} · …${ident}`;
  }
  return `Compte jamais connecté · …${ident}`;
}

const CYCLES: Record<string, string> = {
  ELEMENTAIRE: 'Élémentaire',
  MOYEN: 'Moyen',
  SECONDAIRE: 'Secondaire',
};

export function libelleCycle(code: string | undefined): string {
  return code ? (CYCLES[code] ?? code) : '';
}

/** « Mathématiques · 6E A », « Titulaire · CI A », « Surveillance · Moyen ». */
export function libelleAffectation(a: AffectationDto): string {
  switch (a.type) {
    case 'ENSEIGNEMENT':
      return `${a.matiere ?? 'Matière'} · ${a.classe ?? ''}`;
    case 'TITULAIRE':
      return `Titulaire · ${a.classe ?? ''}`;
    default:
      return `Surveillance · ${libelleCycle(a.codeCycle)}`;
  }
}
