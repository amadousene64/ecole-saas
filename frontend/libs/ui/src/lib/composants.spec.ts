import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CarteEvenement } from './carte-evenement/carte-evenement';
import { CoquilleLegere } from './coquille/coquille-legere';
import { Pastille } from './pastille/pastille';

@Component({ template: '' })
class Vide {}

describe('Pastille', () => {
  it('affiche un point et un texte, avec la classe du ton', async () => {
    @Component({
      imports: [Pastille],
      template: '<ecole-pastille ton="bissap">Absent</ecole-pastille>',
    })
    class Hote {}
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    const pastille = fixture.nativeElement.querySelector(
      'span.pastille',
    ) as HTMLElement;
    expect(pastille.classList).toContain('bissap');
    expect(pastille.textContent?.trim()).toBe('Absent');
  });

  it('est neutre par défaut', async () => {
    @Component({
      imports: [Pastille],
      template: '<ecole-pastille>Clôturée</ecole-pastille>',
    })
    class Hote {}
    const fixture = TestBed.createComponent(Hote);
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('span.pastille').classList,
    ).toContain('neutre');
  });
});

describe('CarteEvenement', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({ providers: [provideRouter([])] }),
  );

  function creer(entrees: Record<string, unknown>) {
    const fixture = TestBed.createComponent(CarteEvenement);
    for (const [cle, valeur] of Object.entries(entrees)) {
      fixture.componentRef.setInput(cle, valeur);
    }
    return fixture;
  }

  it('affiche icône, titre, détail, moment et une seule action (lien)', async () => {
    const fixture = creer({
      icone: 'retard',
      ton: 'safran',
      titre: 'Retard de 15 min',
      detail: 'Entrée en classe avec billet.',
      moment: "Aujourd'hui, 8 h 15",
      action: 'Voir le billet',
      lien: '/billets/1',
    });
    await fixture.whenStable();
    const racine = fixture.nativeElement as HTMLElement;
    expect(racine.querySelector('.pictogramme')?.classList).toContain('safran');
    expect(racine.querySelector('svg')).not.toBeNull();
    expect(racine.querySelector('h3')?.textContent).toBe('Retard de 15 min');
    expect(racine.querySelectorAll('a, button')).toHaveLength(1);
    expect(racine.querySelector('a')?.getAttribute('href')).toBe('/billets/1');
  });

  it("émet l'action sans lien, et n'en affiche aucune sans libellé", async () => {
    const avecBouton = creer({
      icone: 'info',
      titre: 'Info',
      action: 'Compris',
    });
    let cliques = 0;
    avecBouton.componentInstance.actionDemandee.subscribe(() => cliques++);
    await avecBouton.whenStable();
    (
      avecBouton.nativeElement.querySelector('button') as HTMLButtonElement
    ).click();
    expect(cliques).toBe(1);

    const sansAction = creer({ icone: 'info', titre: 'Info' });
    await sansAction.whenStable();
    expect(sansAction.nativeElement.querySelectorAll('a, button')).toHaveLength(
      0,
    );
  });
});

describe('CoquilleLegere', () => {
  it('affiche la barre basse avec icône et texte, et marque la page active', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'notes', component: Vide }])],
    });
    const fixture = TestBed.createComponent(CoquilleLegere);
    fixture.componentRef.setInput('liens', [
      { chemin: '/', libelle: 'Accueil', icone: 'accueil' },
      { chemin: '/notes', libelle: 'Notes', icone: 'notes' },
      { chemin: '/paiements', libelle: 'Paiements', icone: 'paiements' },
      { chemin: '/messages', libelle: 'Messages', icone: 'messages' },
    ]);
    await fixture.whenStable();
    const barre = fixture.nativeElement.querySelector(
      'nav.barre-basse',
    ) as HTMLElement;
    const liens = barre.querySelectorAll('a');
    expect(liens).toHaveLength(4);
    expect(Array.from(liens).map((a) => a.textContent?.trim())).toEqual([
      'Accueil',
      'Notes',
      'Paiements',
      'Messages',
    ]);
    expect(barre.querySelectorAll('svg')).toHaveLength(4);
    expect(fixture.nativeElement.querySelector('main')?.classList).toContain(
      'avec-barre',
    );
  });
});
