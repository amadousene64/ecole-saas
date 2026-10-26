import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import {
  Chargement,
  EcoleChoix,
  EtatPage,
  SelecteurEcole,
  TEXTES,
} from '@ecole/ui';
import { Session } from '../session';

/** Choix de l'école ; affiche aussi le chargement, l'erreur et le cas « aucune école ». */
@Component({
  selector: 'ecole-page-choix-ecole',
  imports: [Chargement, EtatPage, SelecteurEcole],
  template: `
    @switch (session.etat()) {
      @case ('inconnu') {
        <ecole-chargement />
      }
      @case ('chargement') {
        <ecole-chargement />
      }
      @case ('erreur') {
        <ecole-etat-page
          [titre]="textes.erreurTitre"
          [detail]="textes.erreurDetailDefaut"
          [action]="textes.reessayer"
          [alerte]="true"
          (actionDemandee)="session.recharger()"
        />
      }
      @case ('aucune-ecole') {
        <ecole-etat-page
          [titre]="textes.aucuneEcoleTitre"
          [detail]="textes.aucuneEcoleDetail"
          [action]="textes.deconnexion"
          (actionDemandee)="session.deconnecter()"
        />
      }
      @default {
        <ecole-selecteur-ecole
          [ecoles]="session.ecoles()"
          (choisie)="choisir($event)"
        />
      }
    }
  `,
})
export class PageChoixEcole implements OnInit {
  protected readonly session = inject(Session);
  protected readonly textes = TEXTES;
  private readonly router = inject(Router);

  ngOnInit(): void {
    void this.session.charger();
  }

  protected async choisir(ecole: EcoleChoix): Promise<void> {
    await this.session.choisirEcole(ecole.id);
    if (this.session.etat() === 'pret') {
      await this.router.navigateByUrl('/');
    }
  }
}

@Component({
  selector: 'ecole-page-acces-refuse',
  imports: [EtatPage],
  template: `<ecole-etat-page
    [titre]="textes.accesRefuseTitre"
    [detail]="textes.accesRefuseDetail"
    [action]="textes.retourAccueil"
    (actionDemandee)="accueil()"
  />`,
})
export class PageAccesRefuse {
  protected readonly textes = TEXTES;
  private readonly router = inject(Router);

  protected accueil(): void {
    void this.router.navigateByUrl('/');
  }
}

@Component({
  selector: 'ecole-page-erreur',
  imports: [EtatPage],
  template: `<ecole-etat-page
    [titre]="textes.erreurTitre"
    [detail]="textes.erreurDetailDefaut"
    [action]="textes.reessayer"
    [alerte]="true"
    (actionDemandee)="reessayer()"
  />`,
})
export class PageErreur {
  protected readonly textes = TEXTES;
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  protected async reessayer(): Promise<void> {
    await this.session.recharger();
    await this.router.navigateByUrl(
      this.session.etat() === 'pret' ? '/' : '/choix-ecole',
    );
  }
}
