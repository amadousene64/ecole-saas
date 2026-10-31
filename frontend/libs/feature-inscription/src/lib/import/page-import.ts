import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { RapportImportDto, ResultatImportDto } from '@ecole/api-client';
import { Pastille, TonStatut } from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceImport } from '../services/service-import';

/** Taille maximale d'un fichier d'import (octets), comme côté serveur. */
export const TAILLE_IMPORT_MAX = 2_000_000;
const EXTENSIONS = ['.csv', '.xlsx'];

export function controlerFichierImport(fichier: File): string | null {
  const nom = fichier.name.toLowerCase();
  if (!EXTENSIONS.some((e) => nom.endsWith(e))) {
    return 'Formats acceptés : CSV ou XLSX.';
  }
  if (fichier.size === 0) {
    return 'Le fichier est vide.';
  }
  if (fichier.size > TAILLE_IMPORT_MAX) {
    return `Fichier trop volumineux (${TAILLE_IMPORT_MAX / 1_000_000} Mo au plus).`;
  }
  return null;
}

const TONS: Record<string, TonStatut> = {
  OK: 'primaire',
  AVERTISSEMENT: 'safran',
  ERREUR: 'bissap',
};
const LIBELLES: Record<string, string> = {
  OK: 'OK',
  AVERTISSEMENT: 'Avertissement',
  ERREUR: 'Erreur',
};

/**
 * Import d'élèves : dépôt du fichier, rapport de validation ligne par ligne (rien n'est écrit),
 * puis confirmation, qui renvoie le même fichier avec l'empreinte du rapport. Tout ou rien : un
 * fichier avec des erreurs n'importe rien.
 */
@Component({
  selector: 'ecole-page-import',
  imports: [MatButtonModule, RouterLink, Pastille],
  template: `
    <p><a routerLink="/eleves">← Retour à la liste</a></p>
    <h1>Importer des élèves</h1>
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-import">
        {{ message }}
      </p>
    }
    <p class="aide">
      Fichier CSV ou XLSX (2 Mo et 2 000 lignes au plus). La vérification
      n'écrit rien : vous confirmez ensuite l'import du même fichier.
    </p>
    <div class="champ-fichier">
      <label for="fichier-import">Fichier à importer</label>
      <input
        id="fichier-import"
        type="file"
        accept=".csv,.xlsx"
        (change)="choisir($event)"
        data-testid="champ-fichier-import"
      />
    </div>
    <div class="actions">
      <button
        matButton="filled"
        type="button"
        [disabled]="!fichier() || occupe()"
        (click)="verifier()"
        data-testid="verifier-import"
      >
        Vérifier le fichier
      </button>
    </div>

    @if (resultat(); as r) {
      <p class="message ok" role="status" data-testid="resultat-import">
        Import terminé : {{ r.elevesCrees }} élève(s) créé(s),
        {{ r.inscriptions }} inscription(s), {{ r.responsablesCrees }}
        responsable(s) créé(s).
      </p>
    }

    @if (rapport(); as r) {
      <h2>Rapport de vérification</h2>
      <p role="status" data-testid="synthese-import">
        {{ r.lignes }} ligne(s) : {{ r.erreurs }} erreur(s),
        {{ r.avertissements }} avertissement(s).
        @if (r.importable) {
          Le fichier peut être importé.
        } @else {
          Corrigez le fichier : rien ne sera importé tant qu'il reste des
          erreurs.
        }
      </p>
      @if (r.colonnesIgnorees?.length) {
        <p class="aide">
          Colonnes ignorées : {{ r.colonnesIgnorees!.join(', ') }}.
        </p>
      }
      <table data-testid="table-rapport">
        <caption class="sr-only">
          Rapport ligne par ligne
        </caption>
        <thead>
          <tr>
            <th scope="col">Ligne</th>
            <th scope="col">Résultat</th>
            <th scope="col">Détail</th>
          </tr>
        </thead>
        <tbody>
          @for (l of r.details; track l.numero) {
            <tr [attr.data-testid]="'ligne-' + l.numero">
              <td class="nombre">{{ l.numero }}</td>
              <td>
                <ecole-pastille [ton]="ton(l.statut)">{{
                  libelle(l.statut)
                }}</ecole-pastille>
              </td>
              <td>{{ l.messages?.join(' ') }}</td>
            </tr>
          }
        </tbody>
      </table>
      <div class="actions">
        <button
          matButton="filled"
          type="button"
          [disabled]="!r.importable || occupe()"
          (click)="confirmer()"
          data-testid="confirmer-import"
        >
          Confirmer l'import
        </button>
      </div>
    }
  `,
  styles: [
    STYLES_COMMUNS,
    `
      .champ-fichier {
        display: flex;
        flex-direction: column;
        gap: 4px;
        max-width: 480px;
      }
    `,
  ],
})
export class PageImport {
  private readonly service = inject(ServiceImport);

  protected readonly fichier = signal<File | null>(null);
  protected readonly rapport = signal<RapportImportDto | null>(null);
  protected readonly resultat = signal<ResultatImportDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);

  protected ton(statut: string | undefined): TonStatut {
    return TONS[statut ?? ''] ?? 'neutre';
  }

  protected libelle(statut: string | undefined): string {
    return LIBELLES[statut ?? ''] ?? statut ?? '';
  }

  protected choisir(evenement: Event): void {
    const entree = evenement.target as HTMLInputElement;
    const fichier = entree.files?.[0] ?? null;
    this.erreur.set(null);
    this.rapport.set(null);
    this.resultat.set(null);
    if (fichier) {
      const refus = controlerFichierImport(fichier);
      if (refus) {
        this.erreur.set(refus);
        this.fichier.set(null);
        entree.value = '';
        return;
      }
    }
    this.fichier.set(fichier);
  }

  protected async verifier(): Promise<void> {
    const fichier = this.fichier();
    if (!fichier) {
      return;
    }
    this.erreur.set(null);
    this.resultat.set(null);
    this.occupe.set(true);
    try {
      this.rapport.set(await this.service.valider(fichier));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async confirmer(): Promise<void> {
    const fichier = this.fichier();
    const empreinte = this.rapport()?.empreinte;
    if (!fichier || !empreinte) {
      return;
    }
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const issue = await this.service.importer(fichier, empreinte);
      if (issue.importe) {
        this.resultat.set(issue.resultat);
        this.rapport.set(null);
        this.fichier.set(null);
      } else {
        // 422 : rien n'a été écrit, le serveur renvoie le rapport à jour.
        this.rapport.set(issue.rapport);
        this.erreur.set(
          "Rien n'a été importé : le fichier contient des erreurs.",
        );
      }
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
