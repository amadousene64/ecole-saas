import { Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { TypeImage } from '../services/service-etablissement';

const TAILLES_MAX: Record<TypeImage, number> = {
  LOGO: 1_000_000,
  CACHET: 1_000_000,
  SIGNATURE: 512_000,
};
const TYPES_ACCEPTES = ['image/png', 'image/jpeg'];

/**
 * Une image officielle (logo, cachet ou signature) : aperçu par URL signée, dépôt et suppression.
 * Les contrôles faits ici (type, taille) évitent un aller-retour inutile ; le serveur recontrôle
 * tout (octets réels, 2000 px au plus, ré-encodage).
 */
@Component({
  selector: 'ecole-carte-image',
  imports: [MatButtonModule],
  template: `
    <section class="carte" [attr.data-testid]="'image-' + type()">
      <h3>{{ titre() }}</h3>
      @if (url(); as source) {
        <img [src]="source" [alt]="'Aperçu : ' + titre()" />
      } @else {
        <p class="aide">Aucune image.</p>
      }
      @if (modifiable()) {
        <p class="aide">
          PNG ou JPEG, {{ tailleMaxKo() }} Ko au plus, 2000 pixels de côté au
          plus.
        </p>
        <label class="fichier">
          <span>{{ url() ? 'Remplacer' : 'Téléverser' }} : {{ titre() }}</span>
          <input
            type="file"
            accept="image/png,image/jpeg"
            [attr.data-testid]="'fichier-' + type()"
            (change)="choisir($event)"
          />
        </label>
        @if (url()) {
          <button matButton type="button" (click)="supprimer.emit()">
            Supprimer
          </button>
        }
        @if (probleme(); as message) {
          <p class="message erreur" role="alert">{{ message }}</p>
        }
      }
    </section>
  `,
  styleUrl: './carte-image.scss',
})
export class CarteImage {
  readonly type = input.required<TypeImage>();
  readonly titre = input.required<string>();
  readonly url = input<string | undefined>();
  readonly modifiable = input(false);

  readonly deposer = output<File>();
  readonly supprimer = output<void>();

  protected readonly probleme = signal<string | null>(null);

  protected tailleMaxKo(): number {
    return TAILLES_MAX[this.type()] / 1000;
  }

  protected choisir(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.item(0);
    this.probleme.set(null);
    if (!fichier) {
      return;
    }
    const verdict = this.verifier(fichier);
    if (verdict) {
      this.probleme.set(verdict);
      champ.value = '';
      return;
    }
    this.deposer.emit(fichier);
    champ.value = '';
  }

  /** `null` si le fichier peut partir au serveur, sinon la raison du refus. */
  private verifier(fichier: File): string | null {
    if (!TYPES_ACCEPTES.includes(fichier.type)) {
      return 'Format refusé : seuls les fichiers PNG et JPEG sont acceptés.';
    }
    if (fichier.size > TAILLES_MAX[this.type()]) {
      return `Fichier trop volumineux : ${this.tailleMaxKo()} Ko au plus.`;
    }
    return null;
  }
}
