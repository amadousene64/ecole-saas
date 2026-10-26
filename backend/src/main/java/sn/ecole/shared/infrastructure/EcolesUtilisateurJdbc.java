package sn.ecole.shared.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.ecole.shared.domain.EcoleUtilisateur;
import sn.ecole.shared.domain.EcolesUtilisateurRepository;

@Repository
class EcolesUtilisateurJdbc implements EcolesUtilisateurRepository {

  private final JdbcClient jdbc;

  EcolesUtilisateurJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<EcoleUtilisateur> ecolesDe(UUID utilisateurId) {
    return jdbc.sql("SELECT ecole_id, ecole_code, ecole_nom FROM ecoles_de_utilisateur(:u)")
        .param("u", utilisateurId)
        .query(
            (rs, ligne) ->
                new EcoleUtilisateur(
                    rs.getObject("ecole_id", UUID.class),
                    rs.getString("ecole_code"),
                    rs.getString("ecole_nom")))
        .list();
  }
}
