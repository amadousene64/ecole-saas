import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatiereDto } from '@ecole/api-client';
import { Confirmation, Pastille } from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { ServiceEnseignements } from '../services/service-enseignements';
import { CYCLES, ModaleMatiere } from './modale-matiere';

/**
 * Matières de l'école, dans l'ordre d'affichage (celui des bulletins et de l'emploi du temps).
 * Ajout et modification en modale, suppression confirmée ; « Ajouter les matières usuelles »
 * complète la liste avec le modèle sénégalais sans rien modifier.
 */
@Component({
  selector: 'ecole-section-matieres',
  imports: [Confirmation, MatButtonModule, ModaleMatiere, Pastille],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-matieres">
        {{ message }}
      </p>
    }
    @if (info(); as message) {
      <p class="message ok" role="status" data-testid="info-matieres">
        {{ message }}
      </p>
    }
    @if (droits.peutOrganiser()) {
      <div class="actions">
        <button
          matButton="filled"
          type="button"
          (click)="edition.set({ matiere: null })"
          data-testid="ajouter-matiere"
        >
          Ajouter une matière
        </button>
        <button
          matButton="outlined"
          type="button"
          [disabled]="occupe()"
          (click)="ajouterUsuelles()"
          data-testid="matieres-usuelles"
        >
          Ajouter les matières usuelles
        </button>
      </div>
    }
    <table data-testid="table-matieres" class="cartes">
      <thead>
        <tr>
          <th>Code</th>
          <th>Matière</th>
          <th>Groupe</th>
          <th>Cycles</th>
          @if (droits.peutOrganiser()) {
            <th><span class="sr-only">Actions</span></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (m of matieres(); track m.id) {
          <tr [attr.data-testid]="'matiere-' + m.code">
            <td data-label="Code" class="mono">{{ m.code }}</td>
            <td data-label="Matière">
              {{ m.libelle }}
              @if (!m.actif) {
                <ecole-pastille ton="neutre">Inactive</ecole-pastille>
              }
            </td>
            <td data-label="Groupe">{{ m.groupe ?? '' }}</td>
            <td data-label="Cycles">{{ libelleCycles(m) }}</td>
            @if (droits.peutOrganiser()) {
              <td data-label="Actions">
                <button
                  matButton
                  type="button"
                  (click)="edition.set({ matiere: m })"
                >
                  Modifier<span class="sr-only"> {{ m.libelle }}</span>
                </button>
                <button matButton type="button" (click)="aSupprimer.set(m)">
                  Supprimer<span class="sr-only"> {{ m.libelle }}</span>
                </button>
              </td>
            }
          </tr>
        } @empty {
          <tr>
            <td colspan="5" class="aide">Aucune matière.</td>
          </tr>
        }
      </tbody>
    </table>

    @if (edition(); as e) {
      <ecole-modale-matiere
        [matiere]="e.matiere"
        (enregistree)="enregistree()"
        (fermee)="edition.set(null)"
      />
    }
    @if (aSupprimer(); as m) {
      <ecole-confirmation
        titre="Supprimer la matière"
        [consequence]="
          m.libelle +
          ' disparaît de la liste. Pour la garder dans l’historique sans l’enseigner, décochez plutôt « Matière enseignée ».'
        "
        action="Supprimer"
        testId="confirmer-suppression-matiere"
        [ouverte]="true"
        [occupe]="occupe()"
        [erreur]="erreurSuppression()"
        (confirmee)="supprimer(m)"
        (annulee)="aSupprimer.set(null)"
      />
    }
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class SectionMatieres {
  private readonly service = inject(ServiceEnseignements);
  protected readonly droits = inject(Droits);

  protected readonly matieres = signal<MatiereDto[]>([]);
  /** Modale ouverte : matière modifiée, ou null pour un ajout. */
  protected readonly edition = signal<{ matiere: MatiereDto | null } | null>(
    null,
  );
  protected readonly aSupprimer = signal<MatiereDto | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly erreurSuppression = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly occupe = signal(false);

  constructor() {
    void this.charger();
  }

  private async charger(): Promise<void> {
    try {
      this.matieres.set(await this.service.matieres());
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  protected libelleCycles(m: MatiereDto): string {
    return CYCLES.filter((c) => m.cycles?.includes(c.code))
      .map((c) => c.libelle)
      .join(', ');
  }

  protected async enregistree(): Promise<void> {
    this.edition.set(null);
    this.info.set(null);
    await this.charger();
  }

  protected async ajouterUsuelles(): Promise<void> {
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const ajoutees = await this.service.ajouterMatieresUsuelles();
      this.info.set(
        ajoutees.length === 0
          ? 'Toutes les matières usuelles sont déjà là.'
          : `${ajoutees.length} matière${ajoutees.length > 1 ? 's' : ''} ajoutée${ajoutees.length > 1 ? 's' : ''}.`,
      );
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async supprimer(m: MatiereDto): Promise<void> {
    this.erreurSuppression.set(null);
    this.occupe.set(true);
    try {
      await this.service.supprimerMatiere(m);
      this.aSupprimer.set(null);
      await this.charger();
    } catch (e) {
      this.erreurSuppression.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
