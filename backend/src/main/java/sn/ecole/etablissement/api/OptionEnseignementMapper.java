package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.OptionEnseignement;

@Mapper
interface OptionEnseignementMapper {

  OptionEnseignementDto versDto(OptionEnseignement option);

  List<OptionEnseignementDto> versDtos(List<OptionEnseignement> options);
}
