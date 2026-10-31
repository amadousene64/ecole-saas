import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Api, activationActiver } from '@ecole/api-client';
import { EcoleCourante, Session } from '@ecole/auth';
import { normaliserCode, PageActivation, presenterCode } from './activation';

describe('normaliserCode / presenterCode', () => {
  it('tolère minuscules, espaces et tirets', () => {
    expect(normaliserCode(' 7k2m-x9qd 4rtb ')).toBe('7K2MX9QD4RTB');
    expect(presenterCode('7k2mx9qd4rtb')).toBe('7K2M-X9QD-4RTB');
    expect(presenterCode('7k2mx9qd4rtbEXTRA')).toBe('7K2M-X9QD-4RTB');
    expect(presenterCode('')).toBe('');
  });
});

describe('PageActivation', () => {
  const recharger = vi.fn(async () => undefined);
  const naviguer = vi.fn(async () => true);
  let invoke: ReturnType<typeof vi.fn>;

  async function creer(reponse: () => unknown) {
    sessionStorage.clear();
    vi.clearAllMocks();
    invoke = vi.fn(async (fn: unknown) => {
      if (fn !== activationActiver) {
        throw new Error('appel inattendu');
      }
      return reponse();
    });
    TestBed.configureTestingModule({
      providers: [
        { provide: Api, useValue: { invoke } },
        { provide: Session, useValue: { recharger } },
        { provide: Router, useValue: { navigateByUrl: naviguer } },
      ],
    });
    TestBed.inject(EcoleCourante).choisir('ecole-a');
    const fixture = TestBed.createComponent(PageActivation);
    await fixture.whenStable();
    const racine = fixture.nativeElement as HTMLElement;
    const saisir = async (texte: string) => {
      const champ = racine.querySelector<HTMLInputElement>(
        '[data-testid="champ-code"]',
      )!;
      champ.value = texte;
      champ.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await fixture.whenStable();
    };
    const valider = async () => {
      racine
        .querySelector<HTMLFormElement>('[data-testid="form-activation"]')!
        .dispatchEvent(new Event('submit', { cancelable: true }));
      await fixture.whenStable();
      fixture.detectChanges();
    };
    return { racine, saisir, valider, fixture };
  }

  it('active le compte avec le code normalisé, recharge la session et montre les enfants', async () => {
    const { saisir, valider } = await creer(() => ({ role: 'PARENT' }));
    await saisir('7k2m x9qd 4rtb');
    await valider();

    expect(invoke.mock.calls[0][1]).toEqual({ body: { code: '7K2MX9QD4RTB' } });
    expect(recharger).toHaveBeenCalledOnce();
    expect(naviguer).toHaveBeenCalledWith('/enfants');
  });

  it('n’envoie rien tant que le code est incomplet', async () => {
    const { racine, saisir, valider } = await creer(() => ({}));
    await saisir('7K2M-X9');
    expect(
      racine.querySelector<HTMLButtonElement>('[data-testid="activer"]')!
        .disabled,
    ).toBe(true);
    await valider();
    expect(invoke).not.toHaveBeenCalled();
  });

  it('refuse sans dire pourquoi : inconnu, expiré ou déjà utilisé se ressemblent', async () => {
    const messages: string[] = [];
    for (const statut of [404, 409, 410, 422]) {
      TestBed.resetTestingModule();
      const { racine, saisir, valider } = await creer(() => {
        throw new HttpErrorResponse({
          status: statut,
          error: { code: `CODE_${statut}`, detail: `détail interne ${statut}` },
        });
      });
      await saisir('7K2MX9QD4RTB');
      await valider();
      messages.push(
        racine
          .querySelector('[data-testid="erreur-activation"]')!
          .textContent!.trim(),
      );
    }
    expect(new Set(messages).size).toBe(1);
    expect(messages[0]).toContain('pas valable');
    expect(messages[0]).not.toContain('détail interne');
    expect(naviguer).not.toHaveBeenCalled();
  });

  it('explique la limite d’essais (429)', async () => {
    const { racine, saisir, valider } = await creer(() => {
      throw new HttpErrorResponse({ status: 429 });
    });
    await saisir('7K2MX9QD4RTB');
    await valider();
    expect(
      racine.querySelector('[data-testid="erreur-activation"]')!.textContent,
    ).toContain('Trop d’essais');
  });
});
