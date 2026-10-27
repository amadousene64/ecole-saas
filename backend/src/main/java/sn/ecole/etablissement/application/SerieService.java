package sn.ecole.etablissement.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.ecole.etablissement.domain.ClasseRepository;
import sn.ecole.etablissement.domain.NiveauSerieRepository;
import sn.ecole.etablissement.domain.Serie;
import sn.ecole.etablissement.domain.SerieRepository;

/** Séries du lycée (L1, S2…), modifiables par l'école. */
@Service
@Transactional
public class SerieService {

  private final SerieRepository series;
  private final NiveauSerieRepository liens;
  private final ClasseRepository classes;

  public SerieService(
      SerieRepository series, NiveauSerieRepository liens, ClasseRepository classes) {
    this.series = series;
    this.liens = liens;
    this.classes = classes;
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public List<Serie> lister() {
    return series.findAllByOrderByCode();
  }

  @PreAuthorize(Droits.LECTURE_PERSONNEL)
  @Transactional(readOnly = true)
  public Serie lire(UUID id) {
    return charger(id);
  }

  @PreAuthorize(Droits.ECRITURE)
  public Serie creer(String code, String libelle) {
    Serie serie = Serie.creer(code, libelle);
    verifierCodeLibre(serie.getCode(), null);
    return series.save(serie);
  }

  @PreAuthorize(Droits.ECRITURE)
  public Serie modifier(UUID id, int version, String code, String libelle, boolean actif) {
    Serie serie = charger(id);
    Erreurs.verifierVersion(version, serie);
    serie.modifier(code, libelle, actif);
    verifierCodeLibre(serie.getCode(), id);
    return series.save(serie);
  }

  @PreAuthorize(Droits.ECRITURE)
  public void supprimer(UUID id) {
    Serie serie = charger(id);
    if (liens.existsBySerieId(id) || classes.existsBySerieId(id)) {
      throw Erreurs.conflit(
          "SERIE_UTILISEE", "Cette série est proposée par un niveau ou utilisée par une classe.");
    }
    serie.supprimer(Instant.now());
    series.save(serie);
  }

  private Serie charger(UUID id) {
    return series
        .findById(id)
        .orElseThrow(() -> Erreurs.introuvable("SERIE_INTROUVABLE", "Série introuvable."));
  }

  private void verifierCodeLibre(String code, UUID sauf) {
    series
        .findByCode(code)
        .filter(autre -> !autre.getId().equals(sauf))
        .ifPresent(
            autre -> {
              throw Erreurs.conflit(
                  "SERIE_EXISTANTE", "Une série porte déjà le code " + code + ".");
            });
  }
}
