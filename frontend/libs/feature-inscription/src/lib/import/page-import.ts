import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { RapportImportDto, ResultatImportDto } from '@ecole/api-client';
import { Assistant, EtapeAssistant, Pastille, TonStatut } from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
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

const TITRES: readonly { titre: string; sousTitre: string }[] = [
  {
    titre: 'Importer des élèves',
    sousTitre:
      "Fichier CSV ou XLSX (2 Mo et 2 000 lignes au plus). La vérification n'écrit rien.",
  },
  {
    titre: 'Rapport de vérification',
    sousTitre:
      "Chaque ligne est contrôlée. Tout ou rien : un fichier avec des erreurs n'importe rien.",
  },
  { titre: 'Import terminé', sousTitre: '' },
];

/**
 * Assistant d'import en 3 étapes (écran plein, même composant que l'assistant d'inscription) :
 * Fichier (dépôt et contrôle local), Vérification (rapport ligne par ligne, rien n'est écrit),
 * Import (le même fichier, confirmé par l'empreinte du rapport). Tout ou rien.
 */
@Component({
  selector: 'ecole-page-import',
  imports: [Assistant, MatButtonModule, Pastille, RouterLink],
  template: `
    <ecole-assistant
      titre="Import d'élèves"
      sousTitre="Reprise d'une liste existante"
      [etapes]="etapes()"
      [courante]="etape()"
      [titreEtape]="texte().titre"
      [sousTitreEtape]="texte().sousTitre || null"
      libelleFermer="Fermer l'import"
      (fermer)="quitter()"
      (allerA)="allerA($event)"
    >
      @if (erreur(); as message) {
        <p class="message erreur" role="alert" data-testid="erreur-import">
          {{ message }}
        </p>
      }

      @switch (etape()) {
        @case (1) {
          <div class="carte champ-fichier">
            <label for="fichier-import" class="libelle"
              >Fichier à importer</label
            >
            <input
              id="fichier-import"
              type="file"
              accept=".csv,.xlsx"
              (change)="choisir($event)"
              data-testid="champ-fichier-import"
            />
            @if (fichier(); as f) {
              <p class="aide" data-testid="fichier-choisi">{{ f.name }}</p>
            }
            <p class="aide">
              Colonnes attendues : Prénoms, Nom, Sexe, Date de naissance, Lieu
              de naissance, Classe, et pour le responsable : prénoms, nom,
              téléphone, lien.
            </p>
          </div>
        }
        @case (2) {
          @if (rapport(); as r) {
            <p
              class="message"
              [class.ok]="r.importable"
              [class.erreur]="!r.importable"
              role="status"
              data-testid="synthese-import"
            >
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
            <table data-testid="table-rapport" class="cartes">
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
                    <td class="nombre" data-label="Ligne">{{ l.numero }}</td>
                    <td data-label="Résultat">
                      <ecole-pastille [ton]="ton(l.statut)">{{
                        libelle(l.statut)
                      }}</ecole-pastille>
                    </td>
                    <td data-label="Détail">{{ l.messages?.join(' ') }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
        }
        @case (3) {
          @if (resultat(); as r) {
            <div class="carte" data-testid="resultat-import" role="status">
              <p>
                <strong>{{ r.elevesCrees }} élève(s) créé(s)</strong>,
                {{ r.inscriptions }} inscription(s),
                {{ r.responsablesCrees }} responsable(s) créé(s).
              </p>
              <p class="aide">
                Les élèves importés apparaissent dans la liste.
              </p>
            </div>
          }
        }
      }

      <div pied>
        @switch (etape()) {
          @case (1) {
            <a matButton="outlined" routerLink="/eleves">Annuler</a>
            <button
              matButton="filled"
              type="button"
              [disabled]="!fichier() || occupe()"
              (click)="verifier()"
              data-testid="verifier-import"
            >
              Vérifier le fichier
            </button>
          }
          @case (2) {
            <button
              matButton="outlined"
              type="button"
              [disabled]="occupe()"
              (click)="allerA(1)"
              data-testid="etape-precedente"
            >
              Précédent
            </button>
            <button
              matButton="filled"
              type="button"
              [disabled]="!rapport()?.importable || occupe()"
              (click)="confirmer()"
              data-testid="confirmer-import"
            >
              Importer {{ rapport()?.lignes ?? 0 }} élève(s)
            </button>
          }
          @case (3) {
            <button matButton="outlined" type="button" (click)="recommencer()">
              Importer un autre fichier
            </button>
            <a matButton="filled" routerLink="/eleves" data-testid="voir-liste"
              >Voir la liste</a
            >
          }
        }
      </div>
    </ecole-assistant>
  `,
  styleUrls: ['../../../../ui/src/lib/styles/ecran.scss', './page-import.scss'],
})
export class PageImport {
  private readonly service = inject(ServiceImport);
  private readonly routeur = inject(Router);

  protected readonly fichier = signal<File | null>(null);
  protected readonly rapport = signal<RapportImportDto | null>(null);
  protected readonly resultat = signal<ResultatImportDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly etape = signal(1);

  protected readonly texte = computed(() => TITRES[this.etape() - 1]);
  protected readonly etapes = computed<EtapeAssistant[]>(() => [
    { libelle: 'Fichier', detail: this.fichier()?.name ?? null },
    {
      libelle: 'Vérification',
      detail: this.rapport() ? `${this.rapport()?.lignes} ligne(s)` : null,
    },
    { libelle: 'Import' },
  ]);

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

  /** Étapes déjà faites seulement (le composant n'en propose pas d'autres). */
  protected allerA(etape: number): void {
    if (etape < this.etape() && this.etape() < 3) {
      this.erreur.set(null);
      this.etape.set(etape);
    }
  }

  protected quitter(): void {
    void this.routeur.navigate(['/eleves']);
  }

  protected recommencer(): void {
    this.fichier.set(null);
    this.rapport.set(null);
    this.resultat.set(null);
    this.etape.set(1);
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
      this.etape.set(2);
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
        this.etape.set(3);
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
