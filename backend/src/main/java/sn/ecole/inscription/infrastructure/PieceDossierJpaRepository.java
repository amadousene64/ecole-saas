package sn.ecole.inscription.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.inscription.domain.PieceDossier;
import sn.ecole.inscription.domain.PieceDossierRepository;

/** Implémentation Spring Data du port {@link PieceDossierRepository}. */
interface PieceDossierJpaRepository
    extends Repository<PieceDossier, UUID>, PieceDossierRepository {}
