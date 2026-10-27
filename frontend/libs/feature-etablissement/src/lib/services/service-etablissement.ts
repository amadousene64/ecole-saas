import { inject, Service } from '@angular/core';
import {
  Api,
  EtablissementDto,
  ModifierEtablissementRequete,
  etablissementDeposerImage,
  etablissementLire,
  etablissementModifier,
  etablissementSupprimerImage,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';

export type TypeImage = 'LOGO' | 'CACHET' | 'SIGNATURE';

/** Profil de l'école et ses images officielles. */
@Service()
export class ServiceEtablissement {
  private readonly api = inject(Api);

  lire(): Promise<EtablissementDto> {
    return this.api.invoke(etablissementLire, {});
  }

  modifier(
    courant: EtablissementDto,
    corps: ModifierEtablissementRequete,
  ): Promise<EtablissementDto> {
    return this.api.invoke(etablissementModifier, {
      'If-Match': ifMatch(courant.version),
      body: corps,
    });
  }

  deposerImage(type: TypeImage, fichier: File): Promise<EtablissementDto> {
    return this.api.invoke(etablissementDeposerImage, {
      type,
      body: { fichier },
    });
  }

  supprimerImage(type: TypeImage): Promise<EtablissementDto> {
    return this.api.invoke(etablissementSupprimerImage, { type });
  }
}
