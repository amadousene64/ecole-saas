import { Component, inject, input, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import {
  LienParentalDto,
  LienParentalRequete,
  ResponsableDto,
} from '@ecole/api-client';
import { Pastille } from '@ecole/ui';
import { Droits } from '../commun/droits';
import { messageErreur } from '../commun/erreurs';
import { libelleLien, TYPES_LIEN } from '../commun/etiquettes';
import { STYLES_COMMUNS } from '../commun/styles';
import { ServiceResponsables } from '../services/service-responsables';
import { DialogueCode } from './dialogue-code';

/**
 * Responsables de l'élève et leurs liens. L'indicateur « accès à l'espace parent » est modifiable
 * par le DIRECTEUR seul ; un accès bloqué reste signalé en clair, avec son effet.
 */
@Component({
  selector: 'ecole-onglet-responsables',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    Pastille,
  ],
  template: `
    @if (erreur(); as message) {
      <p class="message erreur" role="alert" data-testid="erreur-responsables">
        {{ message }}
      </p>
    }
    <h2>Responsables</h2>
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
                  (click)="delier(l)"
                  [disabled]="occupe()"
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

    @if (droits.peutEcrire()) {
      <h2>Ajouter un responsable</h2>
      <form
        [formGroup]="formulaire"
        (ngSubmit)="ajouter()"
        class="grille"
        data-testid="form-responsable"
      >
        <mat-form-field appearance="outline">
          <mat-label>Prénoms</mat-label>
          <input
            matInput
            formControlName="prenoms"
            data-testid="resp-prenoms"
          />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Nom</mat-label>
          <input matInput formControlName="nom" data-testid="resp-nom" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Téléphone</mat-label>
          <input
            matInput
            type="tel"
            formControlName="telephone"
            data-testid="resp-telephone"
          />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Courriel</mat-label>
          <input matInput type="email" formControlName="email" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Lien avec l'élève</mat-label>
          <mat-select formControlName="type" data-testid="resp-type">
            @for (t of types; track t.valeur) {
              <mat-option [value]="t.valeur">{{ t.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <div class="cases">
          <mat-checkbox formControlName="responsableLegal"
            >Responsable légal</mat-checkbox
          >
          <mat-checkbox formControlName="payeur">Payeur</mat-checkbox>
          <mat-checkbox formControlName="contactUrgence"
            >Contact d'urgence</mat-checkbox
          >
          <mat-checkbox formControlName="autoriseRecuperer"
            >Autorisé à récupérer l'élève</mat-checkbox
          >
          <mat-checkbox formControlName="recoitNotifications"
            >Reçoit les notifications</mat-checkbox
          >
        </div>
        <div class="actions">
          <button
            matButton="filled"
            type="submit"
            [disabled]="occupe()"
            data-testid="ajouter-responsable"
          >
            Ajouter
          </button>
        </div>
      </form>
    }
  `,
  styles: [
    STYLES_COMMUNS,
    `
      .cases {
        display: flex;
        flex-direction: column;
        grid-column: 1 / -1;
      }
    `,
  ],
})
export class OngletResponsables {
  readonly eleveId = input.required<string>();

  private readonly service = inject(ServiceResponsables);
  private readonly dialogue = inject(MatDialog);
  protected readonly droits = inject(Droits);

  protected readonly types = TYPES_LIEN;
  protected readonly liens = signal<LienParentalDto[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly libelleLien = libelleLien;

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    prenoms: ['', [Validators.required, Validators.maxLength(100)]],
    nom: ['', [Validators.required, Validators.maxLength(100)]],
    telephone: ['', Validators.required],
    email: [''],
    type: ['MERE' as LienParentalRequete['type'], Validators.required],
    responsableLegal: [true],
    payeur: [false],
    contactUrgence: [true],
    autoriseRecuperer: [true],
    recoitNotifications: [true],
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
      this.dialogue.open(DialogueCode, {
        data: {
          titulaire: `${responsable.prenoms} ${responsable.nom}`,
          code: reponse.code ?? '',
          expireLe: reponse.expireLe,
        },
        disableClose: true,
        autoFocus: 'first-tabbable',
      });
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async delier(lien: LienParentalDto): Promise<void> {
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      await this.service.delier(this.eleveId(), lien);
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  protected async ajouter(): Promise<void> {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const responsable = await this.service.creer({
        prenoms: v.prenoms,
        nom: v.nom,
        telephone: v.telephone,
        email: v.email || undefined,
      });
      await this.service.lier(this.eleveId(), {
        responsableId: responsable.id,
        type: v.type,
        responsableLegal: v.responsableLegal,
        payeur: v.payeur,
        contactUrgence: v.contactUrgence,
        autoriseRecuperer: v.autoriseRecuperer,
        recoitNotifications: v.recoitNotifications,
      });
      this.formulaire.reset();
      await this.charger();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
