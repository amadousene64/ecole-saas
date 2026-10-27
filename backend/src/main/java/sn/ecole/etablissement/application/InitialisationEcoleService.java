package sn.ecole.etablissement.application;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import sn.ecole.etablissement.InitialisationEcole;
import sn.ecole.etablissement.StatutEtablissement;
import sn.ecole.etablissement.domain.CodeCycle;
import sn.ecole.etablissement.domain.Cycle;
import sn.ecole.etablissement.domain.CycleRepository;
import sn.ecole.etablissement.domain.Etablissement;
import sn.ecole.etablissement.domain.EtablissementRepository;
import sn.ecole.etablissement.domain.ModeleSenegalais;
import sn.ecole.etablissement.domain.Niveau;
import sn.ecole.etablissement.domain.NiveauRepository;
import sn.ecole.etablissement.domain.NiveauSerie;
import sn.ecole.etablissement.domain.NiveauSerieRepository;
import sn.ecole.etablissement.domain.Serie;
import sn.ecole.etablissement.domain.SerieRepository;
import sn.ecole.shared.application.ExecutionPourTenant;

/**
 * Copie le modèle sénégalais dans une nouvelle école. Pas de {@code @PreAuthorize} : c'est un appel
 * système (voir {@link InitialisationEcole}), jamais relié à un endpoint de l'école.
 */
@Service
public class InitialisationEcoleService implements InitialisationEcole {

  private final ExecutionPourTenant execution;
  private final EtablissementRepository etablissements;
  private final CycleRepository cycles;
  private final NiveauRepository niveaux;
  private final SerieRepository series;
  private final NiveauSerieRepository liens;

  public InitialisationEcoleService(
      ExecutionPourTenant execution,
      EtablissementRepository etablissements,
      CycleRepository cycles,
      NiveauRepository niveaux,
      SerieRepository series,
      NiveauSerieRepository liens) {
    this.execution = execution;
    this.etablissements = etablissements;
    this.cycles = cycles;
    this.niveaux = niveaux;
    this.series = series;
    this.liens = liens;
  }

  @Override
  public void initialiser(UUID tenantId, String nom, String code, StatutEtablissement statut) {
    execution.executer(
        tenantId,
        () -> {
          creer(nom, code, statut);
        });
  }

  @Override
  public boolean estInitialisee(UUID tenantId) {
    return execution.lire(tenantId, etablissements::existsBy);
  }

  private void creer(String nom, String code, StatutEtablissement statut) {
    if (etablissements.existsBy()) {
      throw Erreurs.conflit("ETABLISSEMENT_EXISTANT", "L'école est déjà initialisée.");
    }
    etablissements.save(Etablissement.creer(nom, code, statut));

    Map<CodeCycle, Cycle> parCycle = new EnumMap<>(CodeCycle.class);
    for (ModeleSenegalais.ModeleCycle m : ModeleSenegalais.CYCLES) {
      parCycle.put(
          m.code(),
          cycles.save(
              Cycle.creer(
                  m.code(), m.libelle(), m.typePeriode(), m.modeNotation(), m.baseMoyenne())));
    }
    Map<String, Niveau> parNiveau = new HashMap<>();
    for (ModeleSenegalais.ModeleNiveau m : ModeleSenegalais.NIVEAUX) {
      parNiveau.put(
          m.code(),
          niveaux.save(
              Niveau.creer(
                  parCycle.get(m.cycle()).getId(), m.code(), m.libelle(), m.ordre(), null)));
    }
    Map<String, Serie> parSerie = new HashMap<>();
    for (ModeleSenegalais.ModeleSerie m : ModeleSenegalais.SERIES) {
      parSerie.put(m.code(), series.save(Serie.creer(m.code(), m.libelle())));
    }
    ModeleSenegalais.SERIES_PAR_NIVEAU.forEach(
        (codeNiveau, codesSeries) ->
            codesSeries.forEach(
                codeSerie ->
                    liens.save(
                        NiveauSerie.associer(
                            parNiveau.get(codeNiveau).getId(), parSerie.get(codeSerie).getId()))));
  }
}
