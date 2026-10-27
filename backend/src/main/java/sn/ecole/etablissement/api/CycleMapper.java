package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.Cycle;

@Mapper
interface CycleMapper {

  CycleDto versDto(Cycle cycle);

  List<CycleDto> versDtos(List<Cycle> cycles);
}
