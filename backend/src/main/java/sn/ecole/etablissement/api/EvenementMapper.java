package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.EvenementCalendrier;

@Mapper
interface EvenementMapper {

  EvenementDto versDto(EvenementCalendrier evenement);

  List<EvenementDto> versDtos(List<EvenementCalendrier> evenements);
}
