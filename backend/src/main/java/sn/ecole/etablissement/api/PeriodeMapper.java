package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.Periode;

@Mapper
interface PeriodeMapper {

  PeriodeDto versDto(Periode periode);

  List<PeriodeDto> versDtos(List<Periode> periodes);
}
