package sn.ecole.shared.domain;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/** Génération des clés primaires : UUID v7 (ordonnés dans le temps, bons pour les index). */
public final class Identifiants {

  private static final TimeBasedEpochGenerator GENERATEUR = Generators.timeBasedEpochGenerator();

  private Identifiants() {}

  public static UUID uuidV7() {
    return GENERATEUR.generate();
  }
}
