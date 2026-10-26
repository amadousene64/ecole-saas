import { TestBed } from '@angular/core/testing';
import { EcoleCourante } from './ecole-courante';

describe('EcoleCourante', () => {
  beforeEach(() => sessionStorage.clear());

  it("mémorise l'école le temps de l'onglet et l'oublie", () => {
    const courante = TestBed.inject(EcoleCourante);
    expect(courante.id()).toBeNull();
    courante.choisir('ecole-a');
    expect(courante.id()).toBe('ecole-a');
    expect(sessionStorage.getItem('ecole.ecoleCourante')).toBe('ecole-a');
    courante.oublier();
    expect(courante.id()).toBeNull();
    expect(sessionStorage.getItem('ecole.ecoleCourante')).toBeNull();
  });

  it("retrouve l'école mémorisée au démarrage", () => {
    sessionStorage.setItem('ecole.ecoleCourante', 'ecole-b');
    expect(TestBed.inject(EcoleCourante).id()).toBe('ecole-b');
  });

  it('reste utilisable si le stockage est indisponible', () => {
    const espion = vi
      .spyOn(Storage.prototype, 'setItem')
      .mockImplementation(() => {
        throw new Error('bloqué');
      });
    const courante = TestBed.inject(EcoleCourante);
    courante.choisir('ecole-a');
    expect(courante.id()).toBe('ecole-a');
    espion.mockRestore();
  });
});
