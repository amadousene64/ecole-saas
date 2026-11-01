import { HttpErrorResponse } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import {
  Api,
  RapportImportDto,
  ResultatImportDto,
  importElevesImporter,
  importElevesValider,
} from '@ecole/api-client';

/** Résultat d'une confirmation : importé, ou refusé avec le rapport (HTTP 422). */
export type IssueImport =
  | { importe: true; resultat: ResultatImportDto }
  | { importe: false; rapport: RapportImportDto };

/** Import de fichiers d'élèves : validation à blanc, puis confirmation du même fichier. */
@Service()
export class ServiceImport {
  private readonly api = inject(Api);

  valider(fichier: File, anneeId?: string): Promise<RapportImportDto> {
    return this.api.invoke(importElevesValider, {
      annee: anneeId,
      body: { fichier },
    });
  }

  /** Renvoie le même fichier avec l'empreinte du rapport ; un 422 rend le rapport. */
  async importer(
    fichier: File,
    empreinte: string,
    anneeId?: string,
  ): Promise<IssueImport> {
    try {
      const resultat = await this.api.invoke(importElevesImporter, {
        empreinte,
        annee: anneeId,
        body: { fichier },
      });
      return { importe: true, resultat };
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 422 && e.error) {
        return { importe: false, rapport: e.error as RapportImportDto };
      }
      throw e;
    }
  }
}
