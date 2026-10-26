package sn.ecole.shared.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.shared.domain.EcoleUtilisateur;

@Mapper
interface EcoleMapper {

  EcoleDto versDto(EcoleUtilisateur ecole);

  List<EcoleDto> versDtos(List<EcoleUtilisateur> ecoles);
}
