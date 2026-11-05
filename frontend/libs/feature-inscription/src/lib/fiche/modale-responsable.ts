import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  LienParentalDto,
  LienParentalRequete,
  ResponsableDto,
} from '@ecole/api-client';
import { Modale } from '@ecole/ui';
import { map } from 'rxjs';
import { messageErreur } from '../commun/erreurs';
import { TYPES_LIEN } from '../commun/etiquettes';
import { ServiceResponsables } from '../services/service-responsables';

/** Chiffres saisis à partir desquels on cherche un responsable qui aurait déjà ce numéro. */
const CHIFFRES_MIN = 9;
const DELAI_MS = 400;

let suivant = 0;

/**
 * Ajouter ou modifier un responsable de l'élève, dans une modale. À l'ajout, le numéro saisi est
 * comparé aux responsables existants (même téléphone) : on peut lier celui qui existe au lieu
 * d'en créer un doublon, ou continuer la création. En modification : identité du responsable,
 * type de lien et rôles (deux enregistrements, chacun protégé par If-Match).
 *
 * Le parent crée la modale à l'ouverture et la détruit sur `fermee` ou `enregistre`.
 */
@Component({
  selector: 'ecole-modale-responsable',
  imports: [
    Modale,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <ecole-modale
      [titre]="lien() ? 'Modifier le responsable' : 'Ajouter un responsable'"
      [sousTitre]="
        lien()
          ? 'Identité, lien avec l’élève et rôles'
          : 'Un numéro déjà connu est proposé avant toute création'
      "
      largeur="large"
      testId="modale-responsable"
      [ouverte]="true"
      [modifiee]="modifiee()"
      (fermee)="fermee.emit()"
    >
      @if (erreur(); as message) {
        <p class="message erreur" role="alert" data-testid="erreur-responsable">
          {{ message }}
        </p>
      }
      <form
        [id]="idFormulaire"
        [formGroup]="formulaire"
        (ngSubmit)="enregistrer()"
        class="formulaire"
        data-testid="form-responsable"
      >
        @if (existant(); as r) {
          <div
            class="message info champ-plein"
            data-testid="responsable-existant"
          >
            <p>
              Responsable existant :
              <strong>{{ r.prenoms }} {{ r.nom }}</strong> ({{ r.telephone }}).
              Seuls le lien et les rôles seront enregistrés.
            </p>
            <button
              matButton
              type="button"
              (click)="saisirAutre()"
              data-testid="saisir-autre-responsable"
            >
              Saisir un autre responsable
            </button>
          </div>
        } @else {
          <mat-form-field appearance="outline">
            <mat-label>Téléphone</mat-label>
            <input
              matInput
              type="tel"
              autocomplete="off"
              formControlName="telephone"
              data-testid="resp-telephone"
            />
            <mat-hint
              >Format international, par exemple +221 77 000 00 00</mat-hint
            >
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Courriel</mat-label>
            <input matInput type="email" formControlName="email" />
          </mat-form-field>
          @if (doublons().length > 0) {
            <div
              class="message avertissement champ-plein"
              role="status"
              data-testid="doublons-telephone"
            >
              <p>Ce numéro est déjà enregistré :</p>
              <ul class="doublons">
                @for (d of doublons(); track d.id) {
                  <li>
                    <span>{{ d.prenoms }} {{ d.nom }}</span>
                    @if (dejaLie(d)) {
                      <span class="aide">déjà lié à cet élève</span>
                    } @else {
                      <button
                        matButton
                        type="button"
                        (click)="choisir(d)"
                        [attr.data-testid]="'lier-existant-' + d.nom"
                      >
                        Lier ce responsable<span class="sr-only">
                          : {{ d.prenoms }} {{ d.nom }}</span
                        >
                      </button>
                    }
                  </li>
                }
              </ul>
              <p class="aide">
                Sinon, continuez : un nouveau responsable sera créé.
              </p>
            </div>
          }
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
        }
        <mat-form-field appearance="outline">
          <mat-label>Lien avec l'élève</mat-label>
          <mat-select formControlName="type" data-testid="resp-type">
            @for (t of types; track t.valeur) {
              <mat-option [value]="t.valeur">{{ t.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <fieldset class="cases">
          <legend class="libelle">Rôles</legend>
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
        </fieldset>
      </form>
      <div pied>
        <button matButton type="button" (click)="annuler()">Annuler</button>
        <button
          matButton="filled"
          type="submit"
          [attr.form]="idFormulaire"
          [disabled]="occupe()"
          data-testid="ajouter-responsable"
        >
          {{ lien() ? 'Enregistrer' : 'Ajouter' }}
        </button>
      </div>
    </ecole-modale>
  `,
  styleUrls: [
    '../../../../ui/src/lib/styles/ecran.scss',
    './modale-responsable.scss',
  ],
})
export class ModaleResponsable implements OnInit {
  readonly eleveId = input.required<string>();
  /** Lien à modifier ; absent : ajout d'un responsable. */
  readonly lien = input<LienParentalDto | null>(null);
  /** Responsables déjà liés à l'élève (identifiants), pour ne pas les proposer. */
  readonly lies = input<readonly string[]>([]);
  readonly enregistre = output<void>();
  readonly fermee = output<void>();

  private readonly service = inject(ServiceResponsables);
  private readonly modale = viewChild.required(Modale);
  protected readonly types = TYPES_LIEN;
  protected readonly idFormulaire = `form-responsable-${++suivant}`;
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly doublons = signal<ResponsableDto[]>([]);
  /** Responsable existant choisi à la place d'une création. */
  protected readonly existant = signal<ResponsableDto | null>(null);
  private minuterie: ReturnType<typeof setTimeout> | undefined;
  private recherche = 0;

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

  private readonly sale = toSignal(
    this.formulaire.valueChanges.pipe(map(() => this.formulaire.dirty)),
    { initialValue: false },
  );
  protected readonly modifiee = computed(
    () => this.sale() || this.existant() !== null,
  );

  constructor() {
    this.formulaire.controls.telephone.valueChanges.subscribe((t) =>
      this.surTelephone(t),
    );
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.minuterie);
      this.recherche++;
    });
  }

  ngOnInit(): void {
    const l = this.lien();
    const r = l?.responsable;
    if (l && r) {
      this.formulaire.reset({
        prenoms: r.prenoms ?? '',
        nom: r.nom ?? '',
        telephone: r.telephone ?? '',
        email: r.email ?? '',
        type: l.type ?? 'AUTRE',
        responsableLegal: !!l.responsableLegal,
        payeur: !!l.payeur,
        contactUrgence: !!l.contactUrgence,
        autoriseRecuperer: !!l.autoriseRecuperer,
        recoitNotifications: !!l.recoitNotifications,
      });
    }
  }

  protected dejaLie(r: ResponsableDto): boolean {
    return !!r.id && this.lies().includes(r.id);
  }

  /** Ajout seulement : cherche un responsable qui a déjà ce numéro (correspondance exacte). */
  private surTelephone(telephone: string): void {
    if (this.lien()) {
      return;
    }
    clearTimeout(this.minuterie);
    const numero = ++this.recherche;
    if (telephone.replace(/\D/g, '').length < CHIFFRES_MIN) {
      this.doublons.set([]);
      return;
    }
    this.minuterie = setTimeout(async () => {
      try {
        const trouves = await this.service.rechercher({ telephone });
        if (numero === this.recherche) {
          this.doublons.set(trouves);
        }
      } catch {
        // Aide facultative : sans réponse, la saisie continue normalement.
        if (numero === this.recherche) {
          this.doublons.set([]);
        }
      }
    }, DELAI_MS);
  }

  protected choisir(r: ResponsableDto): void {
    this.existant.set(r);
    this.doublons.set([]);
    // L'identité vient du responsable existant : plus rien à saisir ni à valider.
    for (const champ of ['prenoms', 'nom', 'telephone', 'email'] as const) {
      this.formulaire.controls[champ].disable();
    }
  }

  protected saisirAutre(): void {
    this.existant.set(null);
    for (const champ of ['prenoms', 'nom', 'telephone', 'email'] as const) {
      this.formulaire.controls[champ].enable();
    }
  }

  /** Annuler : comme « × », avec confirmation si la saisie a commencé. */
  protected annuler(): void {
    this.modale().demanderFermeture('fermer');
  }

  protected async enregistrer(): Promise<void> {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const v = this.formulaire.getRawValue();
    const roles = {
      type: v.type,
      responsableLegal: v.responsableLegal,
      payeur: v.payeur,
      contactUrgence: v.contactUrgence,
      autoriseRecuperer: v.autoriseRecuperer,
      recoitNotifications: v.recoitNotifications,
    };
    const identite = {
      prenoms: v.prenoms,
      nom: v.nom,
      telephone: v.telephone,
      email: v.email || undefined,
    };
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const lien = this.lien();
      if (lien?.responsable) {
        await this.service.modifier(lien.responsable, {
          ...identite,
          profession: lien.responsable.profession,
        });
        await this.service.modifierLien(this.eleveId(), lien, {
          ...roles,
          responsableId: lien.responsable.id,
        });
      } else {
        const responsable =
          this.existant() ?? (await this.service.creer(identite));
        await this.service.lier(this.eleveId(), {
          ...roles,
          responsableId: responsable.id,
        });
      }
      this.enregistre.emit();
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }
}
