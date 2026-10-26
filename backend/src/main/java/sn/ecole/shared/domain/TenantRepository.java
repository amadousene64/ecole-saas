package sn.ecole.shared.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de lecture des écoles (implémenté par Spring Data dans {@code infrastructure}). */
public interface TenantRepository {

  Optional<Tenant> findById(UUID id);

  Optional<Tenant> findBySousDomaine(String sousDomaine);

  List<Tenant> findByActifTrueOrderByCode();
}
