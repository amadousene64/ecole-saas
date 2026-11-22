import { inject, Service } from '@angular/core';
import {
  Api,
  CreneauDto,
  CreneauRequete,
  MatiereDto,
  MatiereRequete,
  creneauCopier,
  creneauCreer,
  creneauLister,
  creneauModifier,
  creneauSupprimer,
  matiereAjouterModele,
  matiereCreer,
  matiereLister,
  matiereModifier,
  matiereSupprimer,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';
import { requis } from '../commun/requis';

/** Matières de l'école et grille horaire de chaque cycle. */
@Service()
export class ServiceEnseignements {
  private readonly api = inject(Api);

  matieres(): Promise<MatiereDto[]> {
    return this.api.invoke(matiereLister, {});
  }

  creerMatiere(corps: MatiereRequete): Promise<MatiereDto> {
    return this.api.invoke(matiereCreer, { body: corps });
  }

  modifierMatiere(
    matiere: MatiereDto,
    corps: MatiereRequete,
  ): Promise<MatiereDto> {
    return this.api.invoke(matiereModifier, {
      id: requis(matiere.id),
      'If-Match': ifMatch(matiere.version),
      body: corps,
    });
  }

  supprimerMatiere(matiere: MatiereDto): Promise<void> {
    return this.api.invoke(matiereSupprimer, { id: requis(matiere.id) });
  }

  /** Matières usuelles manquantes ajoutées (aucune si toutes sont déjà là). */
  ajouterMatieresUsuelles(): Promise<MatiereDto[]> {
    return this.api.invoke(matiereAjouterModele, {});
  }

  creneaux(cycleId: string): Promise<CreneauDto[]> {
    return this.api.invoke(creneauLister, { cycleId });
  }

  creerCreneau(cycleId: string, corps: CreneauRequete): Promise<CreneauDto> {
    return this.api.invoke(creneauCreer, { cycleId, body: corps });
  }

  modifierCreneau(
    creneau: CreneauDto,
    corps: CreneauRequete,
  ): Promise<CreneauDto> {
    return this.api.invoke(creneauModifier, {
      id: requis(creneau.id),
      'If-Match': ifMatch(creneau.version),
      body: corps,
    });
  }

  supprimerCreneau(creneau: CreneauDto): Promise<void> {
    return this.api.invoke(creneauSupprimer, { id: requis(creneau.id) });
  }

  /** Recopie la grille de `depuisCycleId` dans `versCycleId`, encore vide. */
  copierGrille(
    versCycleId: string,
    depuisCycleId: string,
  ): Promise<CreneauDto[]> {
    return this.api.invoke(creneauCopier, {
      cycleId: versCycleId,
      body: { depuisCycleId },
    });
  }
}
