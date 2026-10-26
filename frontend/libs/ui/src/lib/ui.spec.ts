import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Coquille } from './coquille/coquille';
import { EtatPage } from './etats/etat-page';
import { SelecteurEcole } from './selecteur-ecole/selecteur-ecole';

const ECOLES = [
  { id: 'a', code: 'LYCEE-A', nom: 'Lycée Exemple A' },
  { id: 'b', code: 'ECOLE-B', nom: 'École Exemple B' },
];

describe('SelecteurEcole', () => {
  it('liste les écoles et émet celle qui est choisie', () => {
    const fixture = TestBed.createComponent(SelecteurEcole);
    fixture.componentRef.setInput('ecoles', ECOLES);
    const choisies: string[] = [];
    fixture.componentInstance.choisie.subscribe((e) => choisies.push(e.id));
    fixture.detectChanges();

    const boutons = fixture.nativeElement.querySelectorAll('button');
    expect(
      Array.from(boutons).map((b) => (b as HTMLElement).textContent?.trim()),
    ).toEqual(['Lycée Exemple A', 'École Exemple B']);
    (boutons[1] as HTMLElement).click();
    expect(choisies).toEqual(['b']);
  });
});

describe('EtatPage', () => {
  it("affiche le titre, le détail et déclenche l'action", () => {
    const fixture = TestBed.createComponent(EtatPage);
    fixture.componentRef.setInput('titre', 'Accès refusé');
    fixture.componentRef.setInput('detail', 'Pas les droits.');
    fixture.componentRef.setInput('action', 'Retour');
    fixture.componentRef.setInput('alerte', true);
    let declenchee = false;
    fixture.componentInstance.actionDemandee.subscribe(
      () => (declenchee = true),
    );
    fixture.detectChanges();

    const racine: HTMLElement = fixture.nativeElement;
    expect(racine.querySelector('h1')?.textContent).toBe('Accès refusé');
    expect(racine.querySelector('[role="alert"]')).not.toBeNull();
    racine.querySelector('button')?.click();
    expect(declenchee).toBe(true);
  });

  it("n'affiche pas de bouton sans action", () => {
    const fixture = TestBed.createComponent(EtatPage);
    fixture.componentRef.setInput('titre', 'T');
    fixture.componentRef.setInput('detail', 'D');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});

@Component({
  imports: [Coquille],
  template: `<ecole-coquille
    [ecole]="'Lycée Exemple A'"
    [utilisateur]="'directeur.a'"
    [plusieursEcoles]="true"
    [liens]="[{ chemin: '/accueil', libelle: 'Accueil' }]"
    (deconnexion)="sorties.push('deconnexion')"
    ><p id="page">Contenu</p></ecole-coquille
  >`,
})
class Hote {
  readonly sorties: string[] = [];
}

describe('Coquille', () => {
  it("affiche l'école, l'utilisateur, la navigation et projette le contenu", () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Hote);
    fixture.detectChanges();

    const racine: HTMLElement = fixture.nativeElement;
    expect(racine.querySelector('.ecole')?.textContent).toContain(
      'Lycée Exemple A',
    );
    expect(racine.textContent).toContain('directeur.a');
    expect(racine.querySelector('nav a')?.textContent).toContain('Accueil');
    expect(racine.querySelector('main#contenu #page')?.textContent).toBe(
      'Contenu',
    );
    expect(racine.querySelector('a.saut')?.getAttribute('href')).toBe(
      '#contenu',
    );
  });
});
