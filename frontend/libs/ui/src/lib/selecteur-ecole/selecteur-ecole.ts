import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { EcoleChoix } from '../modeles';
import { TEXTES } from '../textes';

/** Liste des écoles de l'utilisateur ; il en choisit une. */
@Component({
  selector: 'ecole-selecteur-ecole',
  imports: [MatCardModule, MatButtonModule],
  templateUrl: './selecteur-ecole.html',
  styleUrl: './selecteur-ecole.scss',
})
export class SelecteurEcole {
  protected readonly textes = TEXTES;

  readonly ecoles = input.required<EcoleChoix[]>();
  readonly choisie = output<EcoleChoix>();
}
