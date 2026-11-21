import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { page, userEvent } from 'vitest/browser';
import { Confirmation } from './confirmation';
import { Modale, RaisonFermeture } from './modale';

@Component({
  imports: [Modale, Confirmation],
  template: `
    <button id="ouvrir" type="button" (click)="ouverte.set(true)">
      Ouvrir
    </button>
    <ecole-modale
      titre="Changer de classe"
      sousTitre="Élève fictif"
      testId="m1"
      [(ouverte)]="ouverte"
      [modifiee]="modifiee()"
      (fermee)="raisons.push($event)"
    >
      <label for="champ-1">Classe</label>
      <input id="champ-1" />
      <input id="champ-2" aria-label="Second champ" />
      <div pied>
        <button id="annuler" type="button">Annuler</button>
        <button id="valider" type="button">Valider</button>
      </div>
    </ecole-modale>
    <ecole-modale titre="Seconde" testId="m2" [(ouverte)]="seconde">
      <p>Contenu</p>
    </ecole-modale>
    <ecole-confirmation
      titre="Radier l'élève"
      consequence="L'élève quitte définitivement l'école."
      action="Radier l'élève"
      testId="c1"
      [motif]="motif()"
      [(ouverte)]="confirmation"
      (confirmee)="confirmes.push($event)"
      (annulee)="annulations = annulations + 1"
    />
  `,
})
class Hote {
  readonly ouverte = signal(false);
  readonly seconde = signal(false);
  readonly confirmation = signal(false);
  readonly modifiee = signal(false);
  readonly motif = signal<'aucun' | 'obligatoire'>('aucun');
  raisons: RaisonFermeture[] = [];
  confirmes: (string | undefined)[] = [];
  annulations = 0;
}

describe('ecole-modale (navigateur)', () => {
  let fixture: ComponentFixture<Hote>;
  let hote: Hote;

  const dialogue = (id: string) =>
    document.querySelector<HTMLDialogElement>(`dialog[data-testid="${id}"]`)!;

  async function rendre(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    // afterRenderEffect : une image de plus.
    await new Promise((r) => requestAnimationFrame(r));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function ouvrir(): Promise<void> {
    document.getElementById('ouvrir')!.focus();
    hote.ouverte.set(true);
    await rendre();
  }

  beforeEach(async () => {
    await page.viewport(1280, 800);
    fixture = TestBed.createComponent(Hote);
    hote = fixture.componentInstance;
    await rendre();
  });

  afterEach(() => fixture.destroy());

  it('s’ouvre en modale native, nommée par son titre, décrite par son sous-titre', async () => {
    await ouvrir();
    const d = dialogue('m1');
    expect(d.open).toBe(true);
    expect(d.matches(':modal')).toBe(true);
    expect(
      document.getElementById(d.getAttribute('aria-labelledby')!)?.textContent,
    ).toContain('Changer de classe');
    expect(
      document.getElementById(d.getAttribute('aria-describedby')!)?.textContent,
    ).toContain('Élève fictif');
    expect(document.documentElement.classList).toContain('modale-ouverte');
  });

  it('place le focus sur le premier champ du corps', async () => {
    await ouvrir();
    expect(document.activeElement?.id).toBe('champ-1');
  });

  it('Échap ferme et rend le focus à l’élément d’origine', async () => {
    await ouvrir();
    await userEvent.keyboard('{Escape}');
    await rendre();
    expect(dialogue('m1').open).toBe(false);
    expect(hote.ouverte()).toBe(false);
    expect(hote.raisons).toEqual(['echap']);
    expect(document.activeElement?.id).toBe('ouvrir');
    expect(document.documentElement.classList).not.toContain('modale-ouverte');
  });

  it('piège le focus : Tab et Maj+Tab bouclent dans la modale', async () => {
    await ouvrir();
    document.getElementById('valider')!.focus();
    await userEvent.tab();
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Fermer');
    await userEvent.tab({ shift: true });
    expect(document.activeElement?.id).toBe('valider');
  });

  it('se ferme d’un clic sur le fond, pas d’un clic dans le cadre', async () => {
    await ouvrir();
    await userEvent.click(document.getElementById('champ-2')!);
    expect(dialogue('m1').open).toBe(true);
    // Le fond : un point du <dialog> hors de son cadre (coin de l'écran).
    const d = dialogue('m1');
    d.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    d.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await rendre();
    expect(d.open).toBe(false);
    expect(hote.raisons).toEqual(['fond']);
  });

  it('avec des changements, demande confirmation DANS la même modale', async () => {
    hote.modifiee.set(true);
    await ouvrir();
    await userEvent.keyboard('{Escape}');
    await rendre();
    expect(dialogue('m1').open).toBe(true);
    expect(document.querySelectorAll('dialog[open]')).toHaveLength(1);
    const alerte = dialogue('m1').querySelector('[role="alert"]');
    expect(alerte?.textContent).toContain('ne sont pas enregistrées');
    expect(document.activeElement?.textContent).toContain(
      'Continuer la saisie',
    );

    // Continuer : retour au formulaire, rien n'est perdu.
    await userEvent.click(page.getByTestId('continuer-saisie'));
    await rendre();
    expect(dialogue('m1').querySelector('#champ-1')).toBeTruthy();
    expect(document.activeElement?.id).toBe('champ-1');

    // Abandonner : la modale se ferme.
    await userEvent.click(page.getByRole('button', { name: 'Fermer' }));
    await rendre();
    await userEvent.click(page.getByTestId('abandonner-modifications'));
    await rendre();
    expect(dialogue('m1').open).toBe(false);
    expect(hote.raisons).toEqual(['abandon']);
  });

  it('refuse une seconde modale tant que la première est ouverte', async () => {
    const erreur = vi
      .spyOn(console, 'error')
      .mockImplementation(() => undefined);
    await ouvrir();
    hote.seconde.set(true);
    await rendre();
    expect(dialogue('m2').open).toBe(false);
    expect(hote.seconde()).toBe(false);
    expect(dialogue('m1').open).toBe(true);
    expect(erreur).toHaveBeenCalled();
    erreur.mockRestore();

    // Une fois la première fermée, la seconde peut s'ouvrir.
    hote.ouverte.set(false);
    await rendre();
    hote.seconde.set(true);
    await rendre();
    expect(dialogue('m2').open).toBe(true);
  });

  it('est placée sous <body> (hors des conteneurs à défilement), puis retirée à la destruction', async () => {
    await ouvrir();
    expect(dialogue('m1').parentElement).toBe(document.body);
    fixture.destroy();
    expect(document.querySelector('dialog[data-testid="m1"]')).toBeNull();
    expect(document.documentElement.classList).not.toContain('modale-ouverte');
  });

  it('occupe tout l’écran sur téléphone', async () => {
    await page.viewport(390, 844);
    await ouvrir();
    const cadre = dialogue('m1').getBoundingClientRect();
    expect(Math.round(cadre.width)).toBe(window.innerWidth);
    expect(Math.round(cadre.height)).toBe(window.innerHeight);
  });
});

describe('ecole-confirmation (navigateur)', () => {
  let fixture: ComponentFixture<Hote>;
  let hote: Hote;

  async function rendre(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((r) => requestAnimationFrame(r));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    await page.viewport(1280, 800);
    fixture = TestBed.createComponent(Hote);
    hote = fixture.componentInstance;
    await rendre();
  });

  afterEach(() => fixture.destroy());

  const confirmation = () =>
    document.querySelector<HTMLDialogElement>('dialog[data-testid="c1"]')!;

  it('est un alertdialog : conséquence lue, focus sur Annuler, bouton nommé par l’action', async () => {
    hote.confirmation.set(true);
    await rendre();
    const d = confirmation();
    expect(d.getAttribute('role')).toBe('alertdialog');
    expect(
      document.getElementById(d.getAttribute('aria-describedby')!)?.textContent,
    ).toContain('quitte définitivement');
    expect(document.activeElement?.textContent?.trim()).toBe('Annuler');
    await expect
      .element(page.getByRole('button', { name: "Radier l'élève" }))
      .toBeVisible();
  });

  it('exige le motif quand il est obligatoire', async () => {
    hote.motif.set('obligatoire');
    hote.confirmation.set(true);
    await rendre();
    await userEvent.click(page.getByTestId('confirmer-action'));
    await rendre();
    const champ = page.getByLabelText(/Motif/);
    await expect.element(champ).toHaveAttribute('aria-invalid', 'true');
    await expect.element(page.getByText('Indiquez le motif.')).toBeVisible();
    expect(hote.confirmes).toEqual([]);

    await userEvent.fill(champ, '  Départ de la famille  ');
    await userEvent.click(page.getByTestId('confirmer-action'));
    expect(hote.confirmes).toEqual(['Départ de la famille']);
  });

  it('Échap annule sans confirmer', async () => {
    hote.confirmation.set(true);
    await rendre();
    await userEvent.keyboard('{Escape}');
    await rendre();
    expect(confirmation().open).toBe(false);
    expect(hote.annulations).toBe(1);
    expect(hote.confirmes).toEqual([]);
  });
});
