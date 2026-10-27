package sn.ecole.etablissement.api;

import java.util.List;
import org.mapstruct.Mapper;
import sn.ecole.etablissement.domain.Classe;

@Mapper
interface ClasseMapper {

  ClasseDto versDto(Classe classe);

  List<ClasseDto> versDtos(List<Classe> classes);
}
