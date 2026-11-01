import { MatDialogRef } from '@angular/material/dialog';
import {
  Component,
  computed,
  inject,
  InjectionToken,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { Icone } from '@ecole/ui';
import { Acces } from './acces';

/** Un résultat de la recherche globale. */
export interface ResultatRecherche {
  id: string;
  libelle: string;
  detail?: string;
  /** Chemin interne vers lequel aller. */
  chemin: string;
}

/**
 * Source de résultats ajoutée par une application (par exemple les élèves). Le terme est envoyé à
 * la source dans le CORPS d'une requête, jamais dans une URL.
 */
export interface SourceRecherche {
  id: string;
  titre: string;
  /** Vrai si l'utilisateur a le droit de chercher dans cette source. */
  disponible(): boolean;
  rechercher(terme: string): Promise<ResultatRecherche[]>;
}

export const SOURCES_RECHERCHE = new InjectionToken<SourceRecherche[]>(
  'SOURCES_RECHERCHE',
);

const DELAI_MS = 250;
const MIN_CARACTERES = 2;

/**
 * Recherche globale (Ctrl+K) : les pages du menu de l'utilisateur, puis les sources de
 * l'application. Les sources sont interrogées avec un délai, résultats périmés ignorés.
 */
@Component({
  selector: 'ecole-recherche-globale',
  imports: [Icone],
  template: `
    <div
      class="recherche"
      role="dialog"
      aria-modal="true"
      aria-label="Recherche globale"
    >
      <label class="champ" for="terme-global">
        <ecole-icone nom="recherche" />
        <span class="sr-only">Rechercher une page ou un élève</span>
        <input
          id="terme-global"
          type="search"
          autocomplete="off"
          placeholder="Rechercher une page ou un élève…"
          [value]="terme()"
          (input)="saisir($any($event.target).value)"
          (keydown.escape)="fermer()"
          data-testid="terme-recherche-globale"
        />
      </label>
      <div class="resultats" role="status" aria-live="polite">
        @for (groupe of groupes(); track groupe.titre) {
          <section [attr.aria-label]="groupe.titre">
            <h2>{{ groupe.titre }}</h2>
            <ul>
              @for (r of groupe.resultats; track r.id) {
                <li>
                  <button
                    type="button"
                    (click)="aller(r)"
                    [attr.data-testid]="'resultat-' + r.id"
                  >
                    <span>{{ r.libelle }}</span>
                    @if (r.detail) {
                      <span class="detail">{{ r.detail }}</span>
                    }
                  </button>
                </li>
              }
            </ul>
          </section>
        }
        @if (aucun()) {
          <p class="vide">Aucun résultat pour « {{ terme() }} ».</p>
        }
        @if (chargement()) {
          <p class="vide">Recherche en cours…</p>
        }
      </div>
    </div>
  `,
  styles: `
    .recherche {
      width: min(560px, calc(100vw - 32px));
      background: var(--ecole-surface);
      color: var(--ecole-texte);
      border-radius: var(--ecole-rayon-carte);
      overflow: hidden;
    }
    .champ {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 0 16px;
      border-bottom: 1px solid var(--ecole-bordure);
    }
    input {
      flex: 1;
      min-height: 56px;
      border: 0;
      background: none;
      font: inherit;
      color: inherit;
    }
    input:focus {
      outline: none;
    }
    .champ:focus-within {
      outline: 3px solid var(--ecole-primaire);
      outline-offset: -3px;
    }
    .resultats {
      max-height: 50vh;
      overflow: auto;
      padding: 8px;
    }
    h2 {
      margin: 8px 8px 4px;
      font-size: var(--ecole-libelle-taille);
      color: var(--ecole-texte-secondaire);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li button {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      width: 100%;
      min-height: 44px;
      padding: 0 12px;
      border: 0;
      border-radius: var(--ecole-rayon-controle);
      background: none;
      font: inherit;
      color: inherit;
      text-align: left;
      cursor: pointer;
      align-items: center;
    }
    li button:hover,
    li button:focus-visible {
      background: var(--ecole-primaire-doux);
    }
    .detail,
    .vide {
      color: var(--ecole-texte-secondaire);
    }
    .vide {
      margin: 8px;
    }
  `,
})
export class RechercheGlobale {
  private readonly acces = inject(Acces);
  private readonly routeur = inject(Router);
  private readonly sources =
    inject(SOURCES_RECHERCHE, { optional: true }) ?? [];
  private readonly ref = inject<MatDialogRef<RechercheGlobale>>(MatDialogRef);

  protected readonly terme = signal('');
  protected readonly chargement = signal(false);
  private readonly distants = signal<
    { titre: string; resultats: ResultatRecherche[] }[]
  >([]);
  private minuterie: ReturnType<typeof setTimeout> | undefined;
  private numero = 0;

  /** Pages du menu de l'utilisateur dont le libellé contient le terme. */
  private readonly pages = computed<ResultatRecherche[]>(() => {
    const t = normaliser(this.terme());
    if (t.length === 0) {
      return [];
    }
    return this.acces
      .menu()
      .filter((e) => normaliser(e.libelle).includes(t))
      .map((e) => ({
        id: 'page-' + e.id,
        libelle: e.libelle,
        chemin: e.chemin,
      }));
  });

  protected readonly groupes = computed(() => [
    ...(this.pages().length > 0
      ? [{ titre: 'Pages', resultats: this.pages() }]
      : []),
    ...this.distants().filter((g) => g.resultats.length > 0),
  ]);

  protected readonly aucun = computed(
    () =>
      this.terme().trim().length >= MIN_CARACTERES &&
      !this.chargement() &&
      this.groupes().length === 0,
  );

  protected saisir(valeur: string): void {
    this.terme.set(valeur);
    clearTimeout(this.minuterie);
    const t = valeur.trim();
    if (t.length < MIN_CARACTERES) {
      this.distants.set([]);
      this.chargement.set(false);
      return;
    }
    this.minuterie = setTimeout(() => void this.chercher(t), DELAI_MS);
  }

  private async chercher(terme: string): Promise<void> {
    const numero = ++this.numero;
    this.chargement.set(true);
    const groupes = await Promise.all(
      this.sources
        .filter((s) => s.disponible())
        .map(async (s) => ({
          titre: s.titre,
          resultats: await s.rechercher(terme).catch(() => []),
        })),
    );
    if (numero === this.numero) {
      this.distants.set(groupes);
      this.chargement.set(false);
    }
  }

  protected aller(r: ResultatRecherche): void {
    this.fermer();
    void this.routeur.navigateByUrl(r.chemin);
  }

  protected fermer(): void {
    this.ref.close();
  }
}

function normaliser(texte: string): string {
  return texte.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
