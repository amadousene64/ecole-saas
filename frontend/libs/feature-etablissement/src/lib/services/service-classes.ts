import { inject, Service } from '@angular/core';
import { requis } from '../commun/requis';
import {
  Api,
  ClasseDto,
  ClasseRequete,
  SalleDto,
  SalleRequete,
  classeCreer,
  classeLister,
  classeModifier,
  classeSupprimer,
  salleCreer,
  salleLister,
  salleModifier,
  salleSupprimer,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';

/** Classes d'une année et salles de l'école. */
@Service()
export class ServiceClasses {
  private readonly api = inject(Api);

  /** Sans année : celle en cours (liste vide s'il n'y en a pas). */
  classes(anneeId?: string, niveauId?: string): Promise<ClasseDto[]> {
    return this.api.invoke(classeLister, { annee: anneeId, niveau: niveauId });
  }

  creerClasse(corps: ClasseRequete): Promise<ClasseDto> {
    return this.api.invoke(classeCreer, { body: corps });
  }

  modifierClasse(classe: ClasseDto, corps: ClasseRequete): Promise<ClasseDto> {
    return this.api.invoke(classeModifier, {
      id: requis(classe.id),
      'If-Match': ifMatch(classe.version),
      body: corps,
    });
  }

  supprimerClasse(classe: ClasseDto): Promise<void> {
    return this.api.invoke(classeSupprimer, { id: requis(classe.id) });
  }

  salles(): Promise<SalleDto[]> {
    return this.api.invoke(salleLister, {});
  }

  creerSalle(corps: SalleRequete): Promise<SalleDto> {
    return this.api.invoke(salleCreer, { body: corps });
  }

  modifierSalle(salle: SalleDto, corps: SalleRequete): Promise<SalleDto> {
    return this.api.invoke(salleModifier, {
      id: requis(salle.id),
      'If-Match': ifMatch(salle.version),
      body: corps,
    });
  }

  supprimerSalle(salle: SalleDto): Promise<void> {
    return this.api.invoke(salleSupprimer, { id: requis(salle.id) });
  }
}
