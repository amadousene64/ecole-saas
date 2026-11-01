import { inject, Service } from '@angular/core';
import { requis } from '../commun/requis';
import {
  AnneeScolaireDto,
  AnneeScolaireRequete,
  Api,
  EvenementDto,
  EvenementRequete,
  PeriodeDto,
  anneeScolaireCloturer,
  anneeScolaireCreer,
  anneeScolaireLister,
  anneeScolaireModifier,
  anneeScolaireOuvrir,
  anneeScolaireSupprimer,
  calendrierAjouter,
  calendrierLister,
  calendrierModifier,
  calendrierSupprimer,
  periodeGenerer,
  periodeLister,
  periodeModifier,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';

/** Années scolaires, périodes et calendrier. */
@Service()
export class ServiceAnnees {
  private readonly api = inject(Api);

  annees(): Promise<AnneeScolaireDto[]> {
    return this.api.invoke(anneeScolaireLister, {});
  }

  creer(corps: AnneeScolaireRequete): Promise<AnneeScolaireDto> {
    return this.api.invoke(anneeScolaireCreer, { body: corps });
  }

  modifier(
    annee: AnneeScolaireDto,
    corps: AnneeScolaireRequete,
  ): Promise<AnneeScolaireDto> {
    return this.api.invoke(anneeScolaireModifier, {
      id: requis(annee.id),
      'If-Match': ifMatch(annee.version),
      body: corps,
    });
  }

  supprimer(annee: AnneeScolaireDto): Promise<void> {
    return this.api.invoke(anneeScolaireSupprimer, { id: requis(annee.id) });
  }

  ouvrir(annee: AnneeScolaireDto): Promise<AnneeScolaireDto> {
    return this.api.invoke(anneeScolaireOuvrir, { id: requis(annee.id) });
  }

  cloturer(annee: AnneeScolaireDto): Promise<AnneeScolaireDto> {
    return this.api.invoke(anneeScolaireCloturer, { id: requis(annee.id) });
  }

  periodes(anneeId: string): Promise<PeriodeDto[]> {
    return this.api.invoke(periodeLister, { anneeId });
  }

  genererPeriodes(anneeId: string): Promise<PeriodeDto[]> {
    return this.api.invoke(periodeGenerer, { anneeId });
  }

  modifierPeriode(
    periode: PeriodeDto,
    corps: { libelle: string; dateDebut: string; dateFin: string },
  ): Promise<PeriodeDto> {
    return this.api.invoke(periodeModifier, {
      anneeId: requis(periode.anneeId),
      periodeId: requis(periode.id),
      'If-Match': ifMatch(periode.version),
      body: corps,
    });
  }

  calendrier(anneeId: string): Promise<EvenementDto[]> {
    return this.api.invoke(calendrierLister, { anneeId });
  }

  ajouterEvenement(
    anneeId: string,
    corps: EvenementRequete,
  ): Promise<EvenementDto> {
    return this.api.invoke(calendrierAjouter, { anneeId, body: corps });
  }

  modifierEvenement(
    evenement: EvenementDto,
    corps: EvenementRequete,
  ): Promise<EvenementDto> {
    return this.api.invoke(calendrierModifier, {
      anneeId: requis(evenement.anneeId),
      evenementId: requis(evenement.id),
      'If-Match': ifMatch(evenement.version),
      body: corps,
    });
  }

  supprimerEvenement(evenement: EvenementDto): Promise<void> {
    return this.api.invoke(calendrierSupprimer, {
      anneeId: requis(evenement.anneeId),
      evenementId: requis(evenement.id),
    });
  }
}
