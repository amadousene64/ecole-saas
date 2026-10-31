package sn.ecole.inscription.application;

import java.util.List;
import sn.ecole.inscription.domain.PieceDossier;

/**
 * État du dossier d'un élève au regard des pièces exigées ({@link PieceDossier#EXIGEES}) : celles
 * qui n'ont aucune pièce déposée, et le nombre de pièces en analyse ou bloquées par l'antivirus. Ne
 * dit rien des autres pièces (un certificat médical déposé n'apparaît jamais ici).
 */
public record DossierPieces(List<PieceDossier.Type> manquantes, int enAnalyse, int refusees) {}
