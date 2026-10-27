import { inject, Service } from '@angular/core';
import { requis } from '../commun/requis';
import {
  Api,
  CreerNiveauRequete,
  CycleDto,
  ModifierCycleRequete,
  ModifierNiveauRequete,
  NiveauDto,
  SerieDto,
  SerieRequete,
  cycleLister,
  cycleModifier,
  niveauCreer,
  niveauDefinirSeries,
  niveauLister,
  niveauModifier,
  niveauSupprimer,
  serieCreer,
  serieLister,
  serieModifier,
  serieSupprimer,
} from '@ecole/api-client';
import { ifMatch } from '../commun/erreurs';

/** Cycles, niveaux et séries : le modèle de l'école, que les années copient à leur ouverture. */
@Service()
export class ServiceStructure {
  private readonly api = inject(Api);

  cycles(): Promise<CycleDto[]> {
    return this.api.invoke(cycleLister, {});
  }

  modifierCycle(cycle: CycleDto, corps: ModifierCycleRequete): Promise<CycleDto> {
    return this.api.invoke(cycleModifier, {
      id: requis(cycle.id),
      'If-Match': ifMatch(cycle.version),
      body: corps,
    });
  }

  niveaux(): Promise<NiveauDto[]> {
    return this.api.invoke(niveauLister, {});
  }

  creerNiveau(corps: CreerNiveauRequete): Promise<NiveauDto> {
    return this.api.invoke(niveauCreer, { body: corps });
  }

  modifierNiveau(niveau: NiveauDto, corps: ModifierNiveauRequete): Promise<NiveauDto> {
    return this.api.invoke(niveauModifier, {
      id: requis(niveau.id),
      'If-Match': ifMatch(niveau.version),
      body: corps,
    });
  }

  definirSeries(niveau: NiveauDto, serieIds: string[]): Promise<NiveauDto> {
    return this.api.invoke(niveauDefinirSeries, {
      id: requis(niveau.id),
      body: { serieIds },
    });
  }

  supprimerNiveau(niveau: NiveauDto): Promise<void> {
    return this.api.invoke(niveauSupprimer, { id: requis(niveau.id) });
  }

  series(): Promise<SerieDto[]> {
    return this.api.invoke(serieLister, {});
  }

  creerSerie(corps: SerieRequete): Promise<SerieDto> {
    return this.api.invoke(serieCreer, { body: corps });
  }

  modifierSerie(serie: SerieDto, corps: SerieRequete): Promise<SerieDto> {
    return this.api.invoke(serieModifier, {
      id: requis(serie.id),
      'If-Match': ifMatch(serie.version),
      body: corps,
    });
  }

  supprimerSerie(serie: SerieDto): Promise<void> {
    return this.api.invoke(serieSupprimer, { id: requis(serie.id) });
  }
}
