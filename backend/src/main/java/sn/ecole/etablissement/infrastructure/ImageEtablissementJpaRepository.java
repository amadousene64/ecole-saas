package sn.ecole.etablissement.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.etablissement.domain.ImageEtablissement;
import sn.ecole.etablissement.domain.ImageEtablissementRepository;

/** Implémentation Spring Data du port {@link ImageEtablissementRepository}. */
interface ImageEtablissementJpaRepository
    extends Repository<ImageEtablissement, UUID>, ImageEtablissementRepository {}
