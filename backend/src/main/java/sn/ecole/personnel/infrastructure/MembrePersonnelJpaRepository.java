package sn.ecole.personnel.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.personnel.domain.MembrePersonnel;
import sn.ecole.personnel.domain.MembrePersonnelRepository;

/** Implémentation Spring Data du port {@link MembrePersonnelRepository}. */
interface MembrePersonnelJpaRepository
    extends Repository<MembrePersonnel, UUID>, MembrePersonnelRepository {}
