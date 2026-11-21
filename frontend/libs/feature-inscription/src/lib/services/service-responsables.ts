import { inject, Service } from '@angular/core';
import {
  Api,
  CodeActivationDto,
  LienParentalDto,
  LienParentalRequete,
  ResponsableDto,
  ResponsableRequete,
  ResponsableResumeDto,
  inscriptionEmettreCodeEleve,
  lienParentalAjouter,
  lienParentalDefinirAcces,
  lienParentalLister,
  lienParentalModifier,
  lienParentalSupprimer,
  responsableCreer,
  responsableEmettreCode,
  responsableModifier,
  responsableRechercherParTelephone,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';
import { requis } from '../commun/requis';

/** Responsables, liens avec l'élève, accès à l'espace parent et codes d'activation. */
@Service()
export class ServiceResponsables {
  private readonly api = inject(Api);

  liens(eleveId: string): Promise<LienParentalDto[]> {
    return this.api.invoke(lienParentalLister, { eleveId });
  }

  /**
   * Responsables qui ont déjà ce téléphone (doublons à la saisie) : identifiant, prénoms et nom.
   * Le numéro part dans le corps d'un POST, jamais dans une URL (journaux d'accès).
   */
  parTelephone(telephone: string): Promise<ResponsableResumeDto[]> {
    return this.api.invoke(responsableRechercherParTelephone, {
      body: { telephone },
    });
  }

  creer(corps: ResponsableRequete): Promise<ResponsableDto> {
    return this.api.invoke(responsableCreer, { body: corps });
  }

  modifier(
    responsable: ResponsableDto,
    corps: ResponsableRequete,
  ): Promise<ResponsableDto> {
    return this.api.invoke(responsableModifier, {
      id: requis(responsable.id),
      'If-Match': ifMatch(responsable.version),
      body: corps,
    });
  }

  lier(eleveId: string, corps: LienParentalRequete): Promise<LienParentalDto> {
    return this.api.invoke(lienParentalAjouter, { eleveId, body: corps });
  }

  modifierLien(
    eleveId: string,
    lien: LienParentalDto,
    corps: LienParentalRequete,
  ): Promise<LienParentalDto> {
    return this.api.invoke(lienParentalModifier, {
      eleveId,
      lienId: requis(lien.id),
      'If-Match': ifMatch(lien.version),
      body: corps,
    });
  }

  delier(eleveId: string, lien: LienParentalDto): Promise<void> {
    return this.api.invoke(lienParentalSupprimer, {
      eleveId,
      lienId: requis(lien.id),
    });
  }

  /** Directeur seul : le serveur refuse tout autre rôle. */
  definirAcces(
    eleveId: string,
    lien: LienParentalDto,
    acces: boolean,
  ): Promise<LienParentalDto> {
    return this.api.invoke(lienParentalDefinirAcces, {
      eleveId,
      lienId: requis(lien.id),
      'If-Match': ifMatch(lien.version),
      body: { acces },
    });
  }

  /** Nouveau code pour un responsable : affiché une seule fois, jamais relisible. */
  codeResponsable(responsable: ResponsableDto): Promise<CodeActivationDto> {
    return this.api.invoke(responsableEmettreCode, {
      id: requis(responsable.id),
    });
  }

  codeEleve(eleveId: string): Promise<CodeActivationDto> {
    return this.api.invoke(inscriptionEmettreCodeEleve, { eleveId });
  }
}
