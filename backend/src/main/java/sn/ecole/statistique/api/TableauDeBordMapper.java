package sn.ecole.statistique.api;

import org.mapstruct.Mapper;
import sn.ecole.statistique.domain.TableauDeBord;

@Mapper
interface TableauDeBordMapper {

  TableauDeBordDto versDto(TableauDeBord tableau);
}
