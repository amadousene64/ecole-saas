import { TestBed } from '@angular/core/testing';
import {
  Api,
  affectationMiennes,
  inscriptionElevesDeLaClasse,
} from '@ecole/api-client';
import { MesClasses } from './mes-classes';

async function creer(reponses: Map<unknown, unknown>) {
  const invoke = vi.fn(async (fn: unknown) => {
    if (!reponses.has(fn)) {
      throw new Error('appel inattendu');
    }
    return reponses.get(fn);
  });
  TestBed.configureTestingModule({
    providers: [{ provide: Api, useValue: { invoke } }],
  });
  const fixture = TestBed.createComponent(MesClasses);
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, invoke, racine: fixture.nativeElement as HTMLElement };
}

describe('MesClasses', () => {
  it('liste mes classes et, au clic, les élèves de la classe', async () => {
    const { fixture, invoke, racine } = await creer(
      new Map<unknown, unknown>([
        [
          affectationMiennes,
          [
            {
              id: 'a1',
              type: 'ENSEIGNEMENT',
              classeId: 'k1',
              classe: '6E A',
              matiere: 'Mathématiques',
            },
          ],
        ],
        [
          inscriptionElevesDeLaClasse,
          [
            {
              id: 'e1',
              nom: 'FICTIF',
              prenoms: 'Awa',
              matricule: '2026-00001',
            },
          ],
        ],
      ]),
    );
    expect(racine.textContent).toContain('6E A');
    expect(racine.textContent).toContain('Mathématiques');

    (
      racine.querySelector('[data-testid="voir-eleves-6E A"]') as HTMLElement
    ).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(invoke).toHaveBeenCalledWith(inscriptionElevesDeLaClasse, {
      classeId: 'k1',
    });
    expect(
      racine.querySelector('[data-testid="table-eleves-classe"]')?.textContent,
    ).toContain('Awa');
  });

  it('sans affectation, le dit au lieu d’afficher une liste vide muette', async () => {
    const { racine } = await creer(new Map([[affectationMiennes, []]]));
    expect(
      racine.querySelector('[data-testid="aucune-classe"]')?.textContent,
    ).toContain('Aucune affectation en cours');
  });
});
