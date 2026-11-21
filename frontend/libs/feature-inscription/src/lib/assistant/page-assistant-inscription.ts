import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import {
  BrouillonInscriptionDto,
  ClasseDto,
  ContenuDto,
  OptionEnseignementDto,
  PieceDto,
  ResponsableSaisiDto,
} from '@ecole/api-client';
import {
  Assistant,
  CHAMPS_FORMULAIRE,
  Champ,
  EtapeAssistant,
  NATIONALITES,
  Pastille,
} from '@ecole/ui';
import { messageErreur } from '../commun/erreurs';
import {
  LIBELLES_ANALYSE,
  LIBELLES_PIECE,
  libelleLien,
} from '../commun/etiquettes';
import { requis } from '../commun/requis';
import { ModaleResponsable } from '../fiche/modale-responsable';
import { ServiceBrouillons, StatutFinal } from '../services/service-brouillons';
import { TypePiece } from '../services/service-dossier';

/** Pièces proposées à l'étape 4, dans l'ordre ; les deux premières sont exigées. */
const PIECES: readonly { type: TypePiece; exigee: boolean }[] = [
  { type: 'ACTE_NAISSANCE', exigee: true },
  { type: 'PHOTO_IDENTITE', exigee: true },
  { type: 'CERTIFICAT_SCOLARITE', exigee: false },
  { type: 'CERTIFICAT_MEDICAL', exigee: false },
];

/** Titre et sous-titre de chaque étape (maquettes 08 à 10). */
const TEXTES_ETAPES: readonly { titre: string; sousTitre: string }[] = [
  {
    titre: "Identité de l'élève",
    sousTitre: "Telle qu'elle figure sur l'acte de naissance.",
  },
  {
    titre: "Responsables de l'élève",
    sousTitre:
      'Ajoutez les personnes à contacter. Au moins un responsable légal est obligatoire.',
  },
  { titre: 'Classe et options', sousTitre: '' },
  {
    titre: 'Pièces du dossier',
    sousTitre:
      "Analysées par l'antivirus avant toute consultation. Elles peuvent aussi être déposées plus tard.",
  },
  {
    titre: "Vérifiez avant d'inscrire",
    sousTitre: 'Chaque section peut encore être modifiée.',
  },
];

/**
 * Assistant d'inscription en 5 étapes (maquettes 08, 09, 10) : Élève, Responsables, Scolarité,
 * Pièces, Vérification. Le brouillon est enregistré côté serveur (chiffré) à chaque passage
 * d'étape ; « Enregistrer et quitter » ou « × » le laissent dans « Brouillons en cours ».
 * L'inscription finale crée tout en une fois (élève, responsables, inscription, pièces).
 *
 * `/eleves/nouveau` ouvre un brouillon puis remplace l'adresse par `/eleves/brouillons/:id` : un
 * rechargement reprend le même brouillon.
 */
@Component({
  selector: 'ecole-page-assistant-inscription',
  providers: [CHAMPS_FORMULAIRE],
  imports: [
    Assistant,
    Champ,
    DatePipe,
    ModaleResponsable,
    Pastille,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
  ],
  templateUrl: './page-assistant-inscription.html',
  styleUrls: [
    '../../../../ui/src/lib/styles/ecran.scss',
    './page-assistant-inscription.scss',
  ],
})
export class PageAssistantInscription implements OnInit {
  private readonly service = inject(ServiceBrouillons);
  private readonly routeur = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly nationalites = NATIONALITES;
  protected readonly typesPieces = PIECES;
  protected readonly libellesPiece = LIBELLES_PIECE;
  protected readonly analyse = LIBELLES_ANALYSE;
  protected readonly libelleLien = libelleLien;

  protected readonly brouillon = signal<BrouillonInscriptionDto | null>(null);
  protected readonly etape = signal(1);
  protected readonly anneeLibelle = signal('');
  protected readonly erreur = signal<string | null>(null);
  protected readonly occupe = signal(false);
  protected readonly responsables = signal<ResponsableSaisiDto[]>([]);
  /** Modale ouverte : index du responsable modifié, -1 pour un ajout, null si fermée. */
  protected readonly modale = signal<number | null>(null);
  protected readonly classes = signal<ClasseDto[]>([]);
  protected readonly options = signal<OptionEnseignementDto[]>([]);
  protected readonly pieces = signal<PieceDto[]>([]);
  protected readonly depot = signal<TypePiece | null>(null);

  protected readonly formEleve = this.fb.group({
    prenoms: ['', [Validators.required, Validators.maxLength(100)]],
    nom: ['', [Validators.required, Validators.maxLength(60)]],
    sexe: ['F' as 'F' | 'M', Validators.required],
    dateNaissance: ['', Validators.required],
    lieuNaissance: ['', [Validators.required, Validators.maxLength(100)]],
    nationalite: ['SN', Validators.required],
    identifiantNational: ['', Validators.maxLength(40)],
  });

  protected readonly formScolarite = this.fb.group({
    classeId: ['', Validators.required],
    redoublant: [false],
  });
  /** Option choisie par groupe (LV2…) ; une seule par groupe. */
  protected readonly choixOptions = signal<Record<string, string>>({});

  protected readonly formFinal = this.fb.group({
    statut: ['INSCRIT' as StatutFinal],
  });

  protected readonly etapes = computed<EtapeAssistant[]>(() => {
    const e = this.formEleveValeur();
    const nomEleve = [e.nom?.toUpperCase(), e.prenoms]
      .filter(Boolean)
      .join(' ');
    const classe = this.classes().find((c) => c.id === this.classeChoisie());
    const deposees = this.pieces().length;
    return [
      { libelle: 'Élève', detail: nomEleve || null },
      {
        libelle: 'Responsables',
        detail: this.responsables().length
          ? `${this.responsables().length} responsable(s)`
          : null,
      },
      { libelle: 'Scolarité', detail: classe?.nom ?? null },
      { libelle: 'Pièces', detail: deposees ? `${deposees} déposée(s)` : null },
      { libelle: 'Vérification' },
    ];
  });

  protected readonly texteEtape = computed(
    () => TEXTES_ETAPES[this.etape() - 1],
  );
  protected readonly atteinte = computed(() => this.brouillon()?.etape ?? 1);
  protected readonly responsableLegal = computed(() =>
    this.responsables().some((r) => r.responsableLegal),
  );
  protected readonly classeChoisie = signal('');
  /** Capacité de la classe choisie (le serveur contrôle le remplissage à la finalisation). */
  protected readonly capaciteChoisie = computed(
    () =>
      this.classes().find((c) => c.id === this.classeChoisie())?.capacite ??
      null,
  );
  protected readonly groupesOptions = computed(() => {
    const groupes = new Map<string, OptionEnseignementDto[]>();
    for (const o of this.options()) {
      const g = o.groupe || 'Options';
      groupes.set(g, [...(groupes.get(g) ?? []), o]);
    }
    return [...groupes.entries()].map(([groupe, liste]) => ({ groupe, liste }));
  });
  protected readonly sousTitre = computed(() => {
    const b = this.brouillon();
    const parties = [this.anneeLibelle() ? `Année ${this.anneeLibelle()}` : ''];
    if (b?.modifieLe) {
      const heure = new Date(b.modifieLe).toLocaleTimeString('fr-FR', {
        hour: '2-digit',
        minute: '2-digit',
      });
      parties.push(`brouillon enregistré à ${heure}`);
    }
    return parties.filter(Boolean).join(' · ');
  });

  private readonly formEleveValeur = signal(this.formEleve.getRawValue());

  constructor() {
    this.formEleve.valueChanges.subscribe(() =>
      this.formEleveValeur.set(this.formEleve.getRawValue()),
    );
    this.formScolarite.controls.classeId.valueChanges.subscribe((id) => {
      this.classeChoisie.set(id);
      void this.chargerOptions(id, {});
    });
  }

  async ngOnInit(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('brouillonId');
    try {
      const b = id ? await this.service.lire(id) : await this.service.ouvrir();
      if (!id) {
        // Un rechargement de la page reprend ce brouillon plutôt que d'en ouvrir un autre.
        void this.routeur.navigate(['/eleves', 'brouillons', b.id], {
          replaceUrl: true,
        });
      }
      this.appliquer(b);
      this.etape.set(b.etape ?? 1);
      const anneeId = requis(b.anneeId);
      const [annee, classes, pieces] = await Promise.all([
        this.service.annee(anneeId),
        this.service.classes(anneeId),
        this.service.pieces(requis(b.id)),
      ]);
      this.anneeLibelle.set(annee.libelle ?? '');
      this.classes.set(classes);
      this.pieces.set(pieces);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  /** Remplit les formulaires à partir du contenu déchiffré du brouillon. */
  private appliquer(b: BrouillonInscriptionDto): void {
    this.brouillon.set(b);
    const c = b.contenu;
    const e = c?.eleve;
    if (e) {
      this.formEleve.reset({
        prenoms: e.prenoms ?? '',
        nom: e.nom ?? '',
        sexe: (e.sexe as 'F' | 'M') ?? 'F',
        dateNaissance: e.dateNaissance ?? '',
        lieuNaissance: e.lieuNaissance ?? '',
        nationalite: e.nationalite ?? 'SN',
        identifiantNational: e.identifiantNational ?? '',
      });
    }
    this.responsables.set([...(c?.responsables ?? [])]);
    const s = c?.scolarite;
    if (s?.classeId) {
      this.formScolarite.reset(
        { classeId: s.classeId, redoublant: !!s.redoublant },
        { emitEvent: false },
      );
      this.classeChoisie.set(s.classeId);
      void this.chargerOptions(s.classeId, {}, s.options ?? []);
    }
  }

  private async chargerOptions(
    classeId: string,
    choix: Record<string, string>,
    choisies: readonly string[] = [],
  ): Promise<void> {
    if (!classeId) {
      this.options.set([]);
      return;
    }
    try {
      const options = (await this.service.options(classeId)).filter(
        (o) => o.actif !== false,
      );
      this.options.set(options);
      const retenu: Record<string, string> = { ...choix };
      for (const o of options) {
        if (o.id && choisies.includes(o.id)) {
          retenu[o.groupe || 'Options'] = o.id;
        }
      }
      this.choixOptions.set(retenu);
    } catch {
      this.options.set([]);
    }
  }

  protected choisirOption(groupe: string, id: string): void {
    this.choixOptions.update((c) => ({ ...c, [groupe]: id }));
  }

  /** Contenu complet du brouillon, tel que saisi (rien n'est exigé avant la finalisation). */
  private contenu(): ContenuDto {
    const e = this.formEleve.getRawValue();
    const s = this.formScolarite.getRawValue();
    return {
      eleve: {
        prenoms: e.prenoms || undefined,
        nom: e.nom || undefined,
        sexe: e.sexe,
        dateNaissance: e.dateNaissance || undefined,
        lieuNaissance: e.lieuNaissance || undefined,
        nationalite: e.nationalite || undefined,
        identifiantNational: e.identifiantNational || undefined,
      },
      responsables: this.responsables(),
      scolarite: {
        classeId: s.classeId || undefined,
        options: Object.values(this.choixOptions()),
        redoublant: s.redoublant,
      },
    };
  }

  /** L'étape affichée est-elle complète ? Sinon, ses champs sont marqués et l'on reste. */
  private etapeValide(): boolean {
    switch (this.etape()) {
      case 1:
        this.formEleve.markAllAsTouched();
        return this.formEleve.valid;
      case 2:
        if (!this.responsableLegal()) {
          this.erreur.set('Ajoutez au moins un responsable légal.');
          return false;
        }
        return true;
      case 3:
        this.formScolarite.markAllAsTouched();
        return this.formScolarite.valid;
      default:
        return true;
    }
  }

  /** Enregistre le brouillon ; `etapeAtteinte` ne recule jamais. */
  private async enregistrer(etapeAtteinte: number): Promise<boolean> {
    const b = this.brouillon();
    if (!b) {
      return false;
    }
    this.erreur.set(null);
    this.occupe.set(true);
    try {
      const atteinte = Math.max(etapeAtteinte, b.etape ?? 1);
      this.brouillon.set(
        await this.service.enregistrer(b, atteinte, this.contenu()),
      );
      return true;
    } catch (e) {
      this.erreur.set(messageErreur(e));
      return false;
    } finally {
      this.occupe.set(false);
    }
  }

  protected async continuer(): Promise<void> {
    if (!this.etapeValide()) {
      return;
    }
    const suivante = this.etape() + 1;
    if (await this.enregistrer(suivante)) {
      this.etape.set(suivante);
    }
  }

  protected async precedent(): Promise<void> {
    if (await this.enregistrer(this.etape())) {
      this.etape.update((e) => Math.max(1, e - 1));
    }
  }

  protected async allerA(etape: number): Promise<void> {
    if (await this.enregistrer(this.etape())) {
      this.etape.set(etape);
    }
  }

  /** « Enregistrer et quitter » et « × » : le brouillon reste dans « Brouillons en cours ». */
  protected async quitter(): Promise<void> {
    if (await this.enregistrer(this.etape())) {
      await this.routeur.navigate(['/eleves']);
    }
  }

  protected async inscrire(): Promise<void> {
    if (!(await this.enregistrer(5))) {
      return;
    }
    this.occupe.set(true);
    try {
      const r = await this.service.finaliser(
        requis(this.brouillon()),
        this.formFinal.getRawValue().statut,
      );
      await this.routeur.navigate(['/eleves', r.eleveId]);
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.occupe.set(false);
    }
  }

  // --- Responsables (étape 2) ---

  protected ajouterResponsable(): void {
    this.modale.set(-1);
  }

  protected modifierResponsable(index: number): void {
    this.modale.set(index);
  }

  protected retirerResponsable(index: number): void {
    this.responsables.update((liste) => liste.filter((_, i) => i !== index));
  }

  protected surSaisi(saisi: ResponsableSaisiDto): void {
    const index = this.modale();
    this.responsables.update((liste) =>
      index === null || index < 0
        ? [...liste, saisi]
        : liste.map((r, i) => (i === index ? saisi : r)),
    );
    this.modale.set(null);
  }

  protected saisieModale(): ResponsableSaisiDto | null {
    const index = this.modale();
    return index !== null && index >= 0 ? this.responsables()[index] : null;
  }

  protected idsExistants(): string[] {
    return this.responsables()
      .map((r) => r.responsableId)
      .filter((id): id is string => !!id);
  }

  protected initiales(r: ResponsableSaisiDto): string {
    return [r.prenoms, r.nom]
      .map((m) => (m ?? '').trim().charAt(0).toUpperCase())
      .join('');
  }

  protected roles(r: ResponsableSaisiDto): string[] {
    return [
      r.responsableLegal
        ? r.type === 'MERE'
          ? 'Responsable légale'
          : 'Responsable légal'
        : '',
      r.payeur ? 'Payeur' : '',
      r.contactUrgence ? "Contact d'urgence" : '',
      r.autoriseRecuperer ? 'Récupère l’élève' : '',
    ].filter(Boolean);
  }

  // --- Pièces (étape 4) ---

  protected pieceDe(type: TypePiece): PieceDto | undefined {
    return this.pieces().find((p) => p.type === type);
  }

  protected async deposer(type: TypePiece, evenement: Event): Promise<void> {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    const b = this.brouillon();
    if (!fichier || !b?.id) {
      return;
    }
    this.erreur.set(null);
    this.depot.set(type);
    try {
      const ancienne = this.pieceDe(type);
      await this.service.deposer(b.id, type, fichier);
      if (ancienne?.id) {
        await this.service.retirerPiece(b.id, ancienne.id);
      }
      this.pieces.set(await this.service.pieces(b.id));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    } finally {
      this.depot.set(null);
    }
  }

  protected async retirerPiece(piece: PieceDto): Promise<void> {
    const b = this.brouillon();
    if (!b?.id || !piece.id) {
      return;
    }
    try {
      await this.service.retirerPiece(b.id, piece.id);
      this.pieces.set(await this.service.pieces(b.id));
    } catch (e) {
      this.erreur.set(messageErreur(e));
    }
  }

  // --- Vérification (étape 5) ---

  protected classeNom(): string {
    return this.classes().find((c) => c.id === this.classeChoisie())?.nom ?? '';
  }

  protected optionsChoisies(): string {
    const ids = Object.values(this.choixOptions());
    return this.options()
      .filter((o) => o.id && ids.includes(o.id))
      .map((o) => o.libelle)
      .join(', ');
  }

  protected nationalite(code: string): string {
    return NATIONALITES.find((n) => n.code === code)?.libelle ?? code;
  }
}
