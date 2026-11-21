import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { optionsNationalite, NATIONALITES } from '../formats/nationalites';
import { Champ } from './champ';

describe('Champ (libellé au-dessus du champ)', () => {
  @Component({
    imports: [Champ, ReactiveFormsModule, MatInputModule, MatSelectModule],
    template: `
      <form [formGroup]="formulaire">
        <ecole-champ libelle="Nom">
          <mat-form-field appearance="outline">
            <input matInput formControlName="nom" />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="E-mail">
          <mat-form-field appearance="outline">
            <input matInput formControlName="email" />
          </mat-form-field>
        </ecole-champ>
        <ecole-champ libelle="Sexe">
          <mat-form-field appearance="outline">
            <mat-select formControlName="sexe">
              <mat-option value="F">Féminin</mat-option>
            </mat-select>
          </mat-form-field>
        </ecole-champ>
      </form>
      <form role="search">
        <ecole-champ libelle="Rechercher">
          <mat-form-field appearance="outline">
            <input matInput [formControl]="recherche" />
          </mat-form-field>
        </ecole-champ>
      </form>
    `,
  })
  class Hote {
    readonly formulaire = new FormGroup({
      nom: new FormControl('', Validators.required),
      email: new FormControl(''),
      sexe: new FormControl('F', Validators.required),
    });
    readonly recherche = new FormControl('');
  }

  async function rendre(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function libelles(racine: HTMLElement): HTMLLabelElement[] {
    return Array.from(racine.querySelectorAll('label.libelle-champ'));
  }

  it('place un vrai libellé avant le champ, sans libellé flottant Material', async () => {
    const racine = await rendre();
    expect(racine.querySelector('mat-label, .mdc-floating-label')).toBeNull();
    const [nom] = libelles(racine);
    const champ = racine.querySelector('mat-form-field') as HTMLElement;
    expect(
      nom.compareDocumentPosition(champ) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('relie le libellé au champ (for) et à la liste (aria-labelledby)', async () => {
    const racine = await rendre();
    const [nom, , sexe] = libelles(racine);
    const entree = racine.querySelector('input') as HTMLInputElement;
    expect(nom.htmlFor).toBe(entree.id);
    expect(nom.htmlFor).not.toBe('');
    const liste = racine.querySelector('mat-select') as HTMLElement;
    expect(liste.getAttribute('aria-labelledby')).toContain(sexe.id);
  });

  it('ajoute « (facultatif) » aux seuls champs non obligatoires, jamais aux filtres', async () => {
    const racine = await rendre();
    const textes = libelles(racine).map((l) => l.textContent?.trim());
    expect(textes).toEqual([
      'Nom',
      'E-mail (facultatif)',
      'Sexe',
      'Rechercher',
    ]);
  });
});

describe('Nationalités', () => {
  it('propose « Sénégalaise » en tête, avec le code pays pour valeur', () => {
    expect(NATIONALITES[0]).toEqual({ code: 'SN', libelle: 'Sénégalaise' });
    expect(new Set(NATIONALITES.map((n) => n.code)).size).toBe(
      NATIONALITES.length,
    );
  });

  it('garde un code enregistré absent de la liste', () => {
    expect(optionsNationalite('SN')).toBe(NATIONALITES);
    expect(optionsNationalite('JP').at(-1)).toEqual({
      code: 'JP',
      libelle: 'JP',
    });
  });
});
