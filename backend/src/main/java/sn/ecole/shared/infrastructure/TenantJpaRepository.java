package sn.ecole.shared.infrastructure;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import sn.ecole.shared.domain.Tenant;
import sn.ecole.shared.domain.TenantRepository;

/** Implémentation Spring Data du port {@link TenantRepository}. */
interface TenantJpaRepository extends Repository<Tenant, UUID>, TenantRepository {}
