import { Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { LienParentalDto, ResponsableDto } from '@ecole/api-client';
import { Confirmation, Pastille } from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { libelleLien } from '../commun/etiquettes';
import { ServiceResponsables } from '../services/service-responsables';
import { DonneesCode, ModaleCode } from './modale-code';
import { ModaleResponsable } from './modale-responsable';

/**
 * Responsables de l'élève et leurs liens. L'indicateur « accès à l'espace parent » est modifiable
 * par le DIRECTEUR seul ; un accès bloqué reste signalé en clair, avec son effet. Ajout et
 * modification dans une modale (ModaleResponsable), retrait après confirmation.
 */
@Component({
  selector: 'ecole-onglet-responsables',
  imports: [
    MatButtonModule,
    MatSlideToggleModule,
    Pastille,
    Confirmation,
    ModaleCode,
    ModaleResponsable,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-responsables">
        {{ message }}
      </p>
    }
    @if (confirmationEdition(); as message) {
      <p class="message ok" role="status">{{ message }}</p>
    }
    <h2>Responsables</h2>
    @if (droits.peutEcrire()) {
      <div class="actions">
        <button
          matButton="filled"
          type="button"
          (click)="edition.set({ lien: null })"
          data-testid="ouvrir-ajout-responsable"
        >
          Ajouter un responsable
        </button>
      </div>
    }
    <table data-testid="table-liens" class="cartes">
      <caption class="sr-only">
        Responsables de l'élève
      </caption>
      <thead>
        <tr>
          <th scope="col">Responsable</th>
          <th scope="col">Lien</th>
          <th scope="col">Rôles</th>
          <th scope="col">Compte</th>
          <th scope="col">Accès à l'espace parent</th>
          @if (droits.peutEcrire()) {
            <th scope="col"><span class="sr-only">Actions</span></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (l of liens(); track l.id) {
          <tr [attr.data-testid]="'lien-' + l.responsable?.nom">
            <td data-label="Responsable">
              {{ l.responsable?.prenoms }} {{ l.responsable?.nom }}
              <div class="aide">{{ l.responsable?.telephone }}</div>
            </td>
            <td data-label="Lien">{{ libelleLien(l.type) }}</td>
            <td data-label="Rôles">
              <span class="aide">{{ roles(l) }}</span>
            </td>
            <td data-label="Compte">
              @if (l.responsable?.compteActive) {
                <ecole-pastille ton="primaire">Compte activé</ecole-pastille>
              } @else {
                <ecole-pastille ton="neutre">Pas de compte</ecole-pastille>
              }
            </td>
            <td data-label="Accès à l'espace parent">
              <mat-slide-toggle
                [checked]="!!l.accesEspaceParent"
                [disabled]="!droits.estDirecteur() || occupe()"
                (change)="changerAcces(l, $event.checked)"
                [attr.data-testid]="'acces-' + l.responsable?.nom"
                [aria-label]="
                  'Accès à l’espace parent de ' + l.responsable?.nom
                "
              >
                {{ l.accesEspaceParent ? 'Autorisé' : 'Bloqué' }}
              </mat-slide-toggle>
              @if (!l.accesEspaceParent) {
                <div data-testid="acces-bloque">
                  <ecole-pastille ton="bissap">Accès bloqué</ecole-pastille>
                  <span class="aide"
                    >Ce responsable ne voit plus cet élève dans l'espace
                    parent.</span
                  >
                </div>
              }
              @if (!droits.estDirecteur()) {
                <div class="aide">Réservé au directeur.</div>
              }
            </td>
            @if (droits.peutEcrire()) {
              <td data-label="Actions">
                <button
                  matButton
                  type="button"
                  (click)="edition.set({ lien: l })"
                  [disabled]="occupe()"
                  [attr.data-testid]="'modifier-' + l.responsable?.nom"
                >
                  Modifier<span class="sr-only">
                    {{ l.responsable?.prenoms }} {{ l.responsable?.nom }}</span
                  >
                </button>
                @if (l.responsable; as r) {
                  <button
                    matButton
                    type="button"
                    (click)="genererCode(r)"
                    [disabled]="occupe()"
                    [attr.data-testid]="'code-' + r.nom"
                  >
                    Code d'activation<span class="sr-only">
                      pour {{ r.prenoms }} {{ r.nom }}</span
                    >
                  </button>
                }
                <button
                  matButton
                  type="button"
                  (click)="aRetirer.set(l)"
                  [disabled]="occupe()"
                  [attr.data-testid]="'retirer-' + l.responsable?.nom"
                >
                  Retirer le lien<span class="sr-only">
                    de {{ l.responsable?.nom }}</span
                  >
                </button>
              </td>
            }
          </tr>
        } @empty {
          <tr>
            <td [attr.colspan]="droits.peutEcrire() ? 6 : 5" class="aide">
              Aucun responsable lié à cet élève.
            </td>
          </tr>
        }
      </tbody>
    </table>

    @if (edition(); as e) {
      <ecole-modale-responsable
        [eleveId]="eleveId()"
        [lien]="e.lien"
        [lies]="idsLies()"
        (enregistre)="apresEdition(e.lien)"
        (fermee)="edition.set(null)"
      />
    }

    <ecole-confirmation
      titre="Retirer le lien"
      [consequence]="consequenceRetrait()"
      action="Retirer le lien"
      testId="confirmer-retrait"
      [ouverte]="aRetirer() !== null"
      [occupe]="occupe()"
      [erreur]="erreurRetrait()"
      (confirmee)="delier()"
      (annulee)="aRetirer.set(null)"
    />

    @if (code(); as c) {
      <ecole-modale-code [donnees]="c" (fermee)="code.set(null)" />
    }
  `,
  styleUrl: '../../../../ui/src/lib/styles/ecran.scss',
})
export class OngletResponsables {
  readonly eleveId = input.required<string>();

  private readonly service = inject(ServiceResponsables);
  protected readonly droits = inject(Droits);

  protected readonly liens = signal<LienParentalDto[]>([]);
  /** Modale ouverte : ajout (lien nul) ou modification d'un lien. */
  protected readonly edition = signal<{ lien: LienParentalDto | null } | null>(
    null,
  );
  /** Lien dont le retrait attend confirmation. */
  protected readonly aRetirer = signal<LienParentalDto | null>(null);
  protected readonly erreurRetrait = signal<string | null>(null);
  protected readonly code = signal<DonneesCode | null>(null);
  protected readonly confirmationEdition = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly libelleLien = libelleLien;

  protected readonly idsLies = computed(() =>
    this.liens()
      .map((l) => l.responsable?.id)
      .filter((id): id is string => !!id),
  );

  protected readonly consequenceRetrait = computed(() => {
    const r = this.aRetirer()?.responsable;
    const nom = r ? `${r.prenoms} ${r.nom}` : 'Ce responsable';
    return `${nom} ne sera plus lié à cet élève : il ne le verra plus dans l'espace parent et ne recevra plus ses notifications. Le responsable lui-même n'est pas supprimé.`;
  });

  constructor() {
    queueMicrotask(() => void this.charger());
  }

  protected roles(l: LienParentalDto): string {
    return [
      l.responsableLegal && 'légal',
      l.payeur && 'payeur',
      l.contactUrgence && 'urgence',
      l.autoriseRecuperer && 'récupère',
      l.recoitNotifications && 'notifié',
    ]
      .filter(Boolean)
      .join(', ');
  }

  private async charger(): Promise<void> {
    try {
      this.liens.set(await this.service.liens(this.eleveId()));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  /** Le serveur refuse tout autre rôle que le directeur ; l'interrupteur revient alors en arrière. */
  protected async changerAcces(
    lien: LienParentalDto,
    acces: boolean,
  ): Promise<void> {
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const maj = await this.service.definirAcces(this.eleveId(), lien, acces);
      this.liens.update((ls) => ls.map((l) => (l.id === maj.id ? maj : l)));
    } catch (e) {
      this.erreur.set(messageErreur(e));
      // Relit les liens pour que l'interrupteur reflète l'état réel du serveur.
      await this.charger();
    } finally {
      this.occupe.set(false);
    }
  }

  protected async genererCode(responsable: ResponsableDto): Promise<void> {
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const reponse = await this.service.codeResponsable(responsable);
      this.code.set({
        titulaire: `${responsable.prenoms} ${responsable.nom}`,
        code: reponse.code ?? '',
        expireLe: reponse.expireLe,
      });
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  /** Retrait confirmé ; en cas d'échec, la confirmation reste ouverte avec l'erreur. */
  protected async delier(): Promise<void> {
    const lien = this.aRetirer();
    if (!lien) {
      return;
    }
    this.erreurRetrait.set(null);
    this.occupe.set(true);
    try {
      await this.service.delier(this.eleveId(), lien);
      this.aRetirer.set(null);
      await this.charger();
    } catch (e) {
      this.erreurRetrait.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async apresEdition(lien: LienParentalDto | null): Promise<void> {
    this.edition.set(null);
    this.confirmationEdition.set(
      lien ? 'Responsable modifié.' : 'Responsable ajouté.',
    );
    await this.charger();
  }
}
