import { TestBed } from '@angular/core/testing';
import { Notifications, ZoneNotifications } from './notifications';

describe('Notifications', () => {
  afterEach(() => vi.useRealTimers());

  it('affiche un message court, annoncé aux lecteurs d’écran, puis le retire seul', async () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(ZoneNotifications);
    const notifications = TestBed.inject(Notifications);
    notifications.info('Page non disponible pour votre rôle.');
    fixture.detectChanges();

    const racine = fixture.nativeElement as HTMLElement;
    expect(racine.querySelector('[role="status"]')).not.toBeNull();
    expect(racine.textContent).toContain(
      'Page non disponible pour votre rôle.',
    );

    await vi.advanceTimersByTimeAsync(5100);
    fixture.detectChanges();
    expect(racine.textContent).not.toContain('Page non disponible');
  });

  it("n'empile pas deux fois la même phrase et se ferme à la main", () => {
    const notifications = TestBed.inject(Notifications);
    notifications.succes('Enregistré.');
    notifications.succes('Enregistré.');
    expect(notifications.liste()).toHaveLength(1);
    notifications.erreur('Échec.');
    expect(notifications.liste().map((n) => n.ton)).toEqual([
      'succes',
      'erreur',
    ]);
    notifications.fermer(notifications.liste()[0].id);
    expect(notifications.liste()).toHaveLength(1);
  });
});
