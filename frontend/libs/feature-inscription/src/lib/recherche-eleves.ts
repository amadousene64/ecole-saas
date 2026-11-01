import { inject, Provider } from '@angular/core';
import { Acces, SOURCES_RECHERCHE, SourceRecherche } from '@ecole/auth';
import { ServiceEleves } from './services/service-eleves';

/**
 * Ajoute les élèves à la recherche globale (Ctrl+K). Le terme part dans le corps d'un POST, jamais
 * dans une URL ; la source n'est proposée qu'aux rôles qui ont la page « Élèves ».
 */
export function provideRechercheEleves(): Provider {
  return {
    provide: SOURCES_RECHERCHE,
    multi: true,
    useFactory: (): SourceRecherche => {
      const service = inject(ServiceEleves);
      const acces = inject(Acces);
      return {
        id: 'eleves',
        titre: 'Élèves',
        disponible: () => acces.pagePermise('eleves'),
        rechercher: async (terme) => {
          const page = await service.rechercher({
            recherche: terme,
            taille: 8,
          });
          return (page.elements ?? []).map((e) => ({
            id: 'eleve-' + e.id,
            libelle: `${e.prenoms} ${e.nom}`,
            detail: e.matricule,
            chemin: '/eleves/' + e.id,
          }));
        },
      };
    },
  };
}
