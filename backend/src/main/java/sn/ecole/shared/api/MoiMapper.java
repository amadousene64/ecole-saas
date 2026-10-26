package sn.ecole.shared.api;

import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import sn.ecole.shared.application.ProfilCourant;
import sn.ecole.shared.domain.RoleEcole;

@Mapper
interface MoiMapper {

  @Mapping(target = "ecoleId", source = "tenantId")
  MoiDto versDto(ProfilCourant profil);

  default List<String> roles(Set<RoleEcole> roles) {
    return roles.stream().sorted().map(Enum::name).toList();
  }
}
