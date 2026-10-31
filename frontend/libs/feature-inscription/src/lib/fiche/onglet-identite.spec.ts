import { TestBed } from '@angular/core/testing';
import {
  EleveDto,
  donneesSensiblesEtat,
  donneesSensiblesLire,
  donneesSensiblesModifier,
} from '@ecole/api-client';
import { fournisseursDeTest, stabiliser } from '../test-utilitaires';
import { OngletIdentite } from './onglet-identite';

const ELEVE: EleveDto = {
  id: 'e1',
  version: 2,
  prenoms: 'Awa',
  nom: 'Fictif',
  sexe: 'F',
  dateNaissance: '2012-05-14',
  lieuNaissance: 'Ville Fictive',
  nationalite: 'SN',
};
const CONTENU = {
  sante: 'ASTHME-FICTIF',
  allergies: 'ARACHIDE-FICTIVE',
  situationFamiliale: '',
};

async function creer(roles: string[]) {
  const { providers, invoke } = fournisseursDeTest(
    roles,
    new Map<unknown, unknown>([
      [donneesSensiblesLire, CONTENU],
      [
        donneesSensiblesEtat,
        {
          santeRenseignee: true,
          allergiesRenseignees: false,
          situationFamilialeRenseignee: false,
        },
      ],
      [
        donneesSensiblesModifier,
        {
          santeRenseignee: true,
          allergiesRenseignees: true,
          situationFamilialeRenseignee: false,
        },
      ],
    ]),
  );
  TestBed.configureTestingModule({ providers });
  const fixture = TestBed.createComponent(OngletIdentite);
  fixture.componentRef.setInput('eleve', ELEVE);
  await stabiliser(fixture);
  await stabiliser(fixture);
  const appels = (fn: unknown) => invoke.mock.calls.filter(([f]) => f === fn);
  return { fixture, racine: fixture.nativeElement as HTMLElement, appels };
}

const clic = (racine: HTMLElement, id: string) =>
  racine.querySelector<HTMLButtonElement>(`[data-testid="${id}"]`)!.click();

describe('OngletIdentite — données sensibles', () => {
  it('les masque tant que l’on n’a pas demandé à les voir, et le dit', async () => {
    const { racine, appels } = await creer(['CENSEUR']);

    expect(racine.textContent).not.toContain('ASTHME-FICTIF');
    expect(racine.textContent).toContain('Valeur masquée');
    expect(
      racine.querySelector('[data-testid="mention-journal"]')!.textContent,
    ).toContain('journal');
    expect(appels(donneesSensiblesLire)).toHaveLength(0);
  });

  it('ne lit (donc ne journalise) qu’au clic sur « Afficher », puis remasque', async () => {
    const { fixture, racine, appels } = await creer(['CENSEUR']);

    clic(racine, 'afficher-sante');
    await stabiliser(fixture);

    expect(appels(donneesSensiblesLire)).toHaveLength(1);
    expect(racine.textContent).toContain('ASTHME-FICTIF');
    expect(racine.textContent).toContain('ARACHIDE-FICTIVE');
    // Un champ vide reste affiché comme tel.
    expect(racine.textContent).toContain('Non renseigné');

    clic(racine, 'masquer-sensibles');
    await stabiliser(fixture);
    expect(racine.textContent).not.toContain('ASTHME-FICTIF');
  });

  it('le secrétariat voit seulement « renseigné / non renseigné » et peut saisir', async () => {
    const { fixture, racine, appels } = await creer(['SECRETARIAT']);

    expect(racine.querySelector('[data-testid="afficher-sante"]')).toBeNull();
    const etat = racine.querySelector('[data-testid="etat-sensibles"]')!;
    expect(etat.textContent).toContain('Santé : renseigné');
    expect(etat.textContent).toContain('Allergies : non renseigné');
    expect(racine.textContent).not.toContain('ASTHME-FICTIF');
    expect(appels(donneesSensiblesLire)).toHaveLength(0);

    const saisie = racine.querySelector<HTMLTextAreaElement>(
      '[data-testid="saisie-allergies"]',
    )!;
    saisie.value = 'Pollen fictif';
    saisie.dispatchEvent(new Event('input'));
    clic(racine, 'enregistrer-sensibles');
    await stabiliser(fixture);

    const envoi = appels(donneesSensiblesModifier);
    expect(envoi).toHaveLength(1);
    expect(envoi[0][1]).toMatchObject({
      eleveId: 'e1',
      'If-Match': '"1"',
      body: { allergies: 'Pollen fictif' },
    });
    // Champs laissés vides : absents de la requête (donc inchangés côté serveur).
    expect(envoi[0][1].body).not.toHaveProperty('sante');
    expect(appels(donneesSensiblesLire)).toHaveLength(0);
  });

  it('n’affiche aucun bloc sensible aux autres rôles', async () => {
    for (const role of ['SURVEILLANT', 'COMPTABLE']) {
      TestBed.resetTestingModule();
      const { racine, appels } = await creer([role]);
      expect(racine.querySelector('[data-testid="bloc-sensibles"]')).toBeNull();
      expect(appels(donneesSensiblesLire)).toHaveLength(0);
      expect(appels(donneesSensiblesEtat)).toHaveLength(0);
    }
  });
});
