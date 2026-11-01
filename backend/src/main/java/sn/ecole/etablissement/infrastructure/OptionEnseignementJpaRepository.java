package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.OptionEnseignement;
import sn.ecole.etablissement.domain.OptionEnseignementRepository;

/** Implémentation Spring Data du port {@link OptionEnseignementRepository}. */
interface OptionEnseignementJpaRepository
    extends Repository<OptionEnseignement, UUID>, OptionEnseignementRepository {}
