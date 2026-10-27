package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.AnneeScolaire;
import sn.ecole.etablissement.domain.AnneeScolaireRepository;

/** Implémentation Spring Data du port {@link AnneeScolaireRepository}. */
interface AnneeScolaireJpaRepository
    extends Repository<AnneeScolaire, UUID>, AnneeScolaireRepository {}
