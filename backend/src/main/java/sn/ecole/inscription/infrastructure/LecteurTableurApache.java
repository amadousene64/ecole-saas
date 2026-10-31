package sn.ecole.inscription.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import sn.ecole.inscription.domain.LecteurTableur;
import sn.ecole.shared.domain.ErreurMetier;
import sn.ecole.shared.domain.TypeErreur;

/**
 * CSV (commons-csv : séparateur « ; » ou « , » détecté, UTF-8 avec ou sans BOM, sinon Windows-1252)
 * et XLSX (Apache POI en flux SAX, première feuille). Le format vient du contenu : un XLSX est une
 * archive ZIP (« PK »). Garde-fous : taille bornée avant lecture, archive protégée contre les
 * bombes de décompression (ZipSecureFile), nombre de lignes et de colonnes borné ; les formules ne
 * sont jamais évaluées (seule la valeur en cache est lue).
 */
@Component
class LecteurTableurApache implements LecteurTableur {

  private static final int COLONNES_MAX = 40;
  private static final int CELLULE_MAX = 500;

  /** Analyses simultanées pour toute l'instance : borne la mémoire prise par les fichiers. */
  private static final int ANALYSES_SIMULTANEES = 2;

  private final java.util.concurrent.Semaphore analyses =
      new java.util.concurrent.Semaphore(ANALYSES_SIMULTANEES);

  static {
    // Taux de compression minimal et tailles maximales des entrées de l'archive XLSX.
    ZipSecureFile.setMinInflateRatio(0.01);
    ZipSecureFile.setMaxEntrySize(10_000_000L);
    ZipSecureFile.setMaxTextSize(5_000_000L);
  }

  @Override
  public List<Ligne> lire(byte[] contenu) {
    if (contenu == null || contenu.length == 0) {
      throw invalide("Le fichier est vide.");
    }
    if (contenu.length > TAILLE_MAX) {
      throw invalide("Fichier trop volumineux (2 Mo au plus).");
    }
    if (!analyses.tryAcquire()) {
      throw new ErreurMetier(
          "TROP_DE_TENTATIVES",
          TypeErreur.TROP_DE_TENTATIVES,
          "Trop d'imports en cours ; réessayez dans un instant.");
    }
    try {
      boolean zip = contenu.length > 3 && contenu[0] == 'P' && contenu[1] == 'K';
      return enLignes(zip ? lireXlsx(contenu) : lireCsv(contenu));
    } finally {
      analyses.release();
    }
  }

  private static List<List<String>> lireCsv(byte[] contenu) {
    String texte = decoder(contenu);
    if (texte.indexOf('\0') >= 0) {
      throw invalide("Seuls les fichiers CSV et XLSX sont acceptés.");
    }
    String premiere = texte.lines().findFirst().orElse("");
    char separateur =
        premiere.chars().filter(c -> c == ';').count()
                >= premiere.chars().filter(c -> c == ',').count()
            ? ';'
            : ',';
    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(separateur)
            .setIgnoreSurroundingSpaces(true)
            .setMaxRows(LIGNES_MAX + 50L)
            .get();
    List<List<String>> lignes = new ArrayList<>();
    try (CSVParser analyseur = CSVParser.parse(new StringReader(texte), format)) {
      for (CSVRecord enregistrement : analyseur) {
        if (lignes.size() > LIGNES_MAX + 1) {
          throw trop();
        }
        List<String> cellules = new ArrayList<>();
        enregistrement.forEach(cellules::add);
        lignes.add(cellules);
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof ErreurMetier erreur) {
        throw erreur;
      }
      throw invalide("Fichier CSV illisible.");
    }
    return lignes;
  }

  private static List<List<String>> lireXlsx(byte[] contenu) {
    LignesXlsx lignes = new LignesXlsx();
    try (OPCPackage paquet = OPCPackage.open(new ByteArrayInputStream(contenu))) {
      XSSFReader lecteur = new XSSFReader(paquet);
      Iterator<InputStream> feuilles = lecteur.getSheetsData();
      if (!feuilles.hasNext()) {
        throw invalide("Le classeur ne contient aucune feuille.");
      }
      XMLReader analyseur = XMLHelper.newXMLReader();
      analyseur.setContentHandler(
          new XSSFSheetXMLHandler(
              lecteur.getStylesTable(),
              new ReadOnlySharedStringsTable(paquet, false),
              lignes,
              new FormateurIso(),
              false));
      try (InputStream feuille = feuilles.next()) {
        analyseur.parse(new InputSource(feuille));
      }
    } catch (ErreurMetier erreur) {
      throw erreur;
    } catch (Exception e) {
      for (Throwable c = e; c != null; c = c.getCause()) {
        if (c instanceof ErreurMetier erreur) {
          throw erreur;
        }
      }
      throw invalide("Fichier Excel illisible (format XLSX attendu).");
    }
    return lignes.lignes;
  }

  /**
   * Lecture en flux (SAX) de la première feuille : seules les lignes utiles sont gardées en mémoire
   * et la lecture s'arrête dès que la limite de lignes est dépassée. Lignes absentes = lignes vides
   * (les numéros de ligne restent ceux du fichier).
   */
  private static final class LignesXlsx implements SheetContentsHandler {
    private final List<List<String>> lignes = new ArrayList<>();
    private List<String> courante;

    @Override
    public void startRow(int numero) {
      if (numero > LIGNES_MAX + 1) {
        throw trop();
      }
      while (lignes.size() < numero) {
        lignes.add(List.of());
      }
      courante = new ArrayList<>();
    }

    @Override
    public void endRow(int numero) {
      lignes.add(courante);
    }

    @Override
    public void cell(String reference, String valeur, XSSFComment commentaire) {
      int colonne = reference == null ? courante.size() : new CellReference(reference).getCol();
      if (colonne >= COLONNES_MAX) {
        return;
      }
      while (courante.size() < colonne) {
        courante.add("");
      }
      courante.add(valeur == null ? "" : valeur);
    }
  }

  /** Dates Excel en AAAA-MM-JJ ; autres nombres bruts (sans séparateur ni zéro superflu). */
  private static final class FormateurIso extends DataFormatter {
    @Override
    public String formatRawCellContents(
        double valeur, int indexFormat, String format, boolean date1904) {
      if (DateUtil.isADateFormat(indexFormat, format) && DateUtil.isValidExcelDate(valeur)) {
        return DateUtil.getLocalDateTime(valeur, date1904)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE);
      }
      return valeur == Math.rint(valeur) && Math.abs(valeur) < 1e15
          ? String.valueOf((long) valeur)
          : String.valueOf(valeur);
    }
  }

  /** En-têtes = première ligne non vide ; lignes vides ignorées ; numéros de ligne du fichier. */
  private static List<Ligne> enLignes(List<List<String>> brut) {
    int debut = 0;
    while (debut < brut.size() && vide(brut.get(debut))) {
      debut++;
    }
    if (debut == brut.size()) {
      throw invalide("Le fichier ne contient aucune ligne.");
    }
    List<String> entetes = brut.get(debut).stream().map(String::strip).toList();
    if (entetes.size() > COLONNES_MAX) {
      throw invalide("Trop de colonnes (" + COLONNES_MAX + " au plus).");
    }
    List<Ligne> lignes = new ArrayList<>();
    for (int i = debut + 1; i < brut.size(); i++) {
      List<String> cellules = brut.get(i);
      if (vide(cellules)) {
        continue;
      }
      Map<String, String> valeurs = new LinkedHashMap<>();
      for (int j = 0; j < entetes.size(); j++) {
        String valeur = j < cellules.size() ? cellules.get(j).strip() : "";
        if (valeur.length() > CELLULE_MAX) {
          valeur = valeur.substring(0, CELLULE_MAX);
        }
        if (!entetes.get(j).isEmpty()) {
          valeurs.put(entetes.get(j), valeur);
        }
      }
      lignes.add(new Ligne(i + 1, valeurs));
    }
    if (lignes.size() > LIGNES_MAX) {
      throw trop();
    }
    return lignes;
  }

  private static boolean vide(List<String> cellules) {
    return cellules.stream().allMatch(c -> c == null || c.isBlank());
  }

  /**
   * UTF-8 (BOM retiré) si le contenu est de l'UTF-8 valide, sinon Windows-1252 (Excel français).
   */
  private static String decoder(byte[] contenu) {
    int debut =
        contenu.length >= 3
                && (contenu[0] & 0xFF) == 0xEF
                && (contenu[1] & 0xFF) == 0xBB
                && (contenu[2] & 0xFF) == 0xBF
            ? 3
            : 0;
    ByteBuffer octets = ByteBuffer.wrap(contenu, debut, contenu.length - debut);
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(octets)
          .toString();
    } catch (CharacterCodingException e) {
      return new String(contenu, debut, contenu.length - debut, Charset.forName("windows-1252"));
    }
  }

  private static ErreurMetier invalide(String message) {
    return new ErreurMetier("FICHIER_INVALIDE", TypeErreur.INVALIDE, message);
  }

  private static ErreurMetier trop() {
    return new ErreurMetier(
        "IMPORT_TROP_GRAND",
        TypeErreur.INVALIDE,
        "Trop de lignes : " + LIGNES_MAX + " élèves au plus par fichier.");
  }
}
