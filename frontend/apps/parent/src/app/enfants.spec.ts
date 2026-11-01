import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Api, EleveResumeDto } from '@ecole/api-client';
import { Session } from '@ecole/auth';
import { PageEnfants } from './enfants';

const ECOLE_A = { id: 'a', code: 'LYCEE-A', nom: 'Lycée Exemple A' };
const ECOLE_B = { id: 'b', code: 'ECOLE-B', nom: 'École Exemple B' };
const ECOLE_C = { id: 'c', code: 'COLLEGE-C', nom: 'Collège Exemple C' };

const AWA: EleveResumeDto = {
  id: 'e1',
  matricule: 'M-00001',
  prenoms: 'Awa',
  nom: 'Fictif',
  classe: '6E A',
};
const BINTOU: EleveResumeDto = {
  id: 'e2',
  matricule: 'M-00002',
  prenoms: 'Bintou',
  nom: 'Fictif',
};

async function creer(
  ecoles: (typeof ECOLE_A)[],
  parEcole: Record<string, EleveResumeDto[] | number>,
) {
  const invoke = vi.fn(
    async (_fn: unknown, params: { 'X-Tenant-Id': string }) => {
      const reponse = parEcole[params['X-Tenant-Id']];
      if (typeof reponse === 'number') {
        throw new HttpErrorResponse({ status: reponse });
      }
      return reponse ?? [];
    },
  );
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: Api, useValue: { invoke } },
      { provide: Session, useValue: { ecoles: signal(ecoles) } },
    ],
  });
  const fixture = TestBed.createComponent(PageEnfants);
  await fixture.whenStable();
  fixture.detectChanges();
  return { invoke, racine: fixture.nativeElement as HTMLElement };
}

describe('PageEnfants (Mes enfants)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('affiche les enfants école par école, en désignant chaque école', async () => {
    const { invoke, racine } = await creer([ECOLE_A, ECOLE_B], {
      a: [AWA],
      b: [BINTOU],
    });

    expect(
      racine.querySelector('[data-testid="ecole-LYCEE-A"]')!.textContent,
    ).toContain('Awa Fictif');
    expect(
      racine.querySelector('[data-testid="ecole-ECOLE-B"]')!.textContent,
    ).toContain('Bintou Fictif');
    // Chaque appel nomme son école ; aucun enfant d'une école n'apparaît dans l'autre.
    expect(invoke.mock.calls.map(([, p]) => p['X-Tenant-Id']).sort()).toEqual([
      'a',
      'b',
    ]);
    expect(
      racine.querySelector('[data-testid="ecole-LYCEE-A"]')!.textContent,
    ).not.toContain('Bintou');
  });

  it('ne liste pas une école où ce compte n’est pas parent (403)', async () => {
    const { racine } = await creer([ECOLE_A, ECOLE_B], { a: 403, b: [BINTOU] });
    expect(racine.querySelector('[data-testid="ecole-LYCEE-A"]')).toBeNull();
    expect(racine.querySelectorAll('[data-testid="enfant"]')).toHaveLength(1);
  });

  it('une école en panne n’empêche pas d’afficher les autres', async () => {
    const { racine } = await creer([ECOLE_A, ECOLE_C], { a: [AWA], c: 500 });
    expect(
      racine.querySelector('[data-testid="ecole-LYCEE-A"]')!.textContent,
    ).toContain('Awa');
    const panne = racine.querySelector('[data-testid="ecole-COLLEGE-C"]')!;
    expect(panne.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('propose l’activation quand aucun enfant n’est relié', async () => {
    const { racine } = await creer([ECOLE_A], { a: [] });
    expect(
      racine.querySelector('[data-testid="aucun-enfant"]')!.textContent,
    ).toContain("code d'activation");
  });

  it('ne garde rien dans le navigateur', async () => {
    await creer([ECOLE_A], { a: [AWA] });
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);
  });
});
