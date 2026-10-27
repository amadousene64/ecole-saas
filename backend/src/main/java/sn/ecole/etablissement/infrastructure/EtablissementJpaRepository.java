package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.Etablissement;
import sn.ecole.etablissement.domain.EtablissementRepository;

/** Implémentation Spring Data du port {@link EtablissementRepository}. */
interface EtablissementJpaRepository
    extends Repository<Etablissement, UUID>, EtablissementRepository {}
