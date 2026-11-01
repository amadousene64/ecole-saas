import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, EleveResumeDto, activationMesEnfants } from '@ecole/api-client';
import { Session } from '@ecole/auth';
import { EcoleChoix } from '@ecole/ui';

interface EnfantsEcole {
  ecole: EcoleChoix;
  enfants: EleveResumeDto[];
  erreur: boolean;
}

/**
 * « Mes enfants », école par école. Chaque appel désigne son école ; le serveur ne renvoie que les
 * enfants liés au parent connecté ET dont l'accès à l'espace parent n'est pas bloqué. Une école
 * où ce compte n'est pas parent (403) n'est tout simplement pas listée ; une école en panne
 * n'empêche pas d'afficher les autres. Rien n'est conservé : ni stockage, ni cache.
 */
@Component({
  selector: 'ecole-enfants',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section aria-labelledby="titre-enfants">
      <h2 id="titre-enfants">Mes enfants</h2>
      @if (chargement()) {
        <p class="texte-secondaire" role="status">Chargement en cours…</p>
      } @else {
        @for (e of ecoles(); track e.ecole.id) {
          <section
            class="carte"
            [attr.aria-labelledby]="'ecole-' + e.ecole.id"
            [attr.data-testid]="'ecole-' + e.ecole.code"
          >
            <h3 [id]="'ecole-' + e.ecole.id">{{ e.ecole.nom }}</h3>
            @if (e.erreur) {
              <p class="message erreur" role="alert">
                Impossible de charger les enfants de cette école. Réessayez plus
                tard.
              </p>
            } @else {
              <ul class="liste-enfants">
                @for (enfant of e.enfants; track enfant.id) {
                  <li data-testid="enfant">
                    <strong>{{ enfant.prenoms }} {{ enfant.nom }}</strong>
                    <span class="texte-secondaire">
                      @if (enfant.classe) {
                        · {{ enfant.classe }}
                      }
                      · matricule
                      <span class="mono">{{ enfant.matricule }}</span>
                    </span>
                  </li>
                }
              </ul>
            }
          </section>
        } @empty {
          <p class="carte texte-secondaire" data-testid="aucun-enfant">
            Aucun enfant n'est encore relié à votre compte.
            <a routerLink="/activer">J'ai un code d'activation</a>
          </p>
        }
      }
    </section>
  `,
  styles: `
    .liste-enfants {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    h3 {
      margin: 0 0 8px;
    }
  `,
})
export class PageEnfants {
  private readonly api = inject(Api);
  private readonly session = inject(Session);

  protected readonly ecoles = signal<EnfantsEcole[]>([]);
  protected readonly chargement = signal(true);

  constructor() {
    void this.charger();
  }

  private async charger(): Promise<void> {
    const ecoles = this.session.ecoles();
    const resultats = await Promise.all(
      ecoles.map(async (ecole): Promise<EnfantsEcole | null> => {
        try {
          const enfants = await this.api.invoke(activationMesEnfants, {
            'X-Tenant-Id': ecole.id,
          });
          return enfants.length > 0 ? { ecole, enfants, erreur: false } : null;
        } catch (e) {
          if (e instanceof HttpErrorResponse && e.status === 403) {
            return null; // pas parent dans cette école
          }
          return { ecole, enfants: [], erreur: true };
        }
      }),
    );
    this.ecoles.set(resultats.filter((r): r is EnfantsEcole => r !== null));
    this.chargement.set(false);
  }
}
