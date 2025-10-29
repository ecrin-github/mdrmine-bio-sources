package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024-2025 MDRMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Set;

import org.apache.commons.text.WordUtils;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.CollectionDescriptor;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.model.bio.Country;
import org.intermine.model.bio.Study;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.util.PropertiesUtil;
import org.intermine.xml.full.Item;



/**
 * Class with utility functions for converter classes
 * @author
 * Note: this.getModel() to access model
 */
public abstract class BaseConverter extends BioFileConverter
{
    private String countriesFP = "";
    private String countriesAltNamesFP = "";
    private String logDir = "";
    protected Logger logger = null;
    protected String currentTrialID = null;
    protected ObjectStore os = null;
    protected Map<String, Item> countriesMap = new HashMap<String, Item>(); // Key: country code, name, aliases (all lowercase), Value: Country Item

    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
                             String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence) {
        super(writer, model, dataSourceName, dataSetTitle, licence);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence, boolean storeOntology) {
        super(writer, model, dataSourceName, dataSetTitle, licence, storeOntology);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model) {
        super(writer, model);
        this.initObjectStore();
    }

    /**
     * Clean a value according to the subclass' logic. Note: method should be static but Java does not allow abstract + static
     * 
     * @param s the value to clean
     * @param strip boolean indicating whether to strip the string of any leading/trailing whitespace
     * @return the cleaned value
     * @see #unescapeHtml()
     * @see #removeQuotes()
     */
    public abstract String cleanValue(String s, boolean strip);

    /**
     * Set logDir from the corresponding source property in project.xml.
     * Method called by InterMine.
     * 
     * @param logDir the path to the directory where the log file will be created
     */
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    /**
     * Set countries CV data path from the corresponding source property in project.xml.
     * Method called by InterMine.
     * 
     * @param countriesFP the path to the countries data file
     */
    public void setCountriesFP(String countriesFP) {
        this.countriesFP = countriesFP;
    }

    /**
     * Set countries alternative names data path from the corresponding source property in project.xml.
     * Method called by InterMine.
     * 
     * @param countriesAltNamesFP the path to the countries alternative names data file
     */
    public void setCountriesAltNamesFP(String countriesAltNamesFP) {
        this.countriesAltNamesFP = countriesAltNamesFP;
    }

    /**
     * Instantiate logger by creating log file and writer.
     * This sets the logWriter instance attribute.
     */
    public void startLogging(String suffix) throws Exception {
        this.logger = new Logger(logDir, suffix);
    }

    /**
     * Close opened log writer.
     */
    public void stopLogging() throws IOException {
        this.logger.stopLogging();
    }

    /**
     * Write to log file with timestamp.
     * 
     * @param text the log text
     */
    public void writeLog(String text) {
        if (this.logger != null) {
            this.logger.writeLog(this.currentTrialID, text);
        } else {
            System.out.println("Logger is null (cannot write logs)");
        }
    }

    /**
     * TODO
     */
    public void initObjectStore() {
        try {
            String alias = "osw.production";
            Properties intermineProps = PropertiesUtil.getProperties();
            Properties noPrefixProps = PropertiesUtil.stripStart(alias, intermineProps);
            String osAlias = noPrefixProps.getProperty("os");
    
            this.os = ObjectStoreFactory.getObjectStore(osAlias);
        } catch(Exception e) {
            // TODO
            ;
        }
    }

    public ClassDescriptor getClassDescriptor(Item item) {
        if (item == null) {
            this.writeLog("Error: called getClassDescriptor() with a null item");
            return null;
        }
        return this.getModel().getClassDescriptorByName(item.getClassName());
    }

    /**
     * TODO
     * populates countriesMap
     */
    public void loadCountries() throws Exception {
        if (this.countriesFP.equals("")) {
            throw new Exception("countriesFP property not set in mdrmine project.xml");
        }

        if (!(new File(this.countriesFP).isFile())) {
            throw new Exception("Countries file does not exist (path tested: " + this.countriesFP + " )");
        }

        HashMap<String, List<String>> altNames = this.loadAltCountryNames();

        FileReader in = new FileReader(this.countriesFP);
        BufferedReader br = new BufferedReader(in);

        final CSVParser parser = new CSVParserBuilder()
                                        .withSeparator('	')
                                        .build();
        final CSVReader csvReader = new CSVReaderBuilder(br)
                                            .withCSVParser(parser)
                                            .build();

        boolean skipNext = false;

        csvReader.readNext();   // Skip headers
        String[] lineValues = csvReader.readNext();

        while (lineValues != null) {
            if (!skipNext) {
                // Creating a Country Item for each line in the file
                // Fields order in file for indices: Country.isoAlpha2, Country.isoAlpha3, null, null, Country.name, Country.capital, null, null, Country.continent, Country.tld, null, null, null, null, null, null, Country.geonameId, null, null
                Item country = this.createClassItem("Country", 
                    new String[][]{{"isoAlpha2", lineValues[0]}, {"isoAlpha3", lineValues[1]}, {"name", lineValues[4]},
                                    {"capital", lineValues[5]}, {"continent", lineValues[8]}, {"tld", lineValues[9]}, {"geonameId", lineValues[16]}});
                
                // Adding entries in map to find Country Item based on various values
                if (!ConverterUtils.isNullOrEmptyOrBlank(lineValues[0])) {
                    this.countriesMap.put(lineValues[0].toLowerCase(), country);
                }
                if (!ConverterUtils.isNullOrEmptyOrBlank(lineValues[1])) {
                    this.countriesMap.put(lineValues[1].toLowerCase(), country);
                }
                if (!ConverterUtils.isNullOrEmptyOrBlank(lineValues[4])) {
                    this.countriesMap.put(lineValues[4].toLowerCase(), country);
                }

                // Adding alternative names from other file
                if (altNames != null) {
                    List<String> aliases = altNames.getOrDefault(lineValues[0].toLowerCase(), null);
                    if (aliases == null) {
                        this.writeLog("Warning: couldn't find any alias for country " + lineValues[4] + " with iso code " + lineValues[0].toLowerCase());
                    } else {
                        for (String alias: aliases) {
                            this.countriesMap.put(alias.toLowerCase(), country);
                        }
                    }
                }
            } else {
                skipNext = false;
            }
            try {
                lineValues = csvReader.readNext();
            } catch (CsvMalformedLineException e) {
                this.writeLog("Found malformed line, skipping it: " + e);
                lineValues = new String[0];
                skipNext = true;
            }
        }

        csvReader.close();
    }

    public HashMap<String, List<String>> loadAltCountryNames() throws Exception {
        HashMap<String, List<String>> altNames = null;

        if (this.countriesAltNamesFP.equals("")) {
            this.writeLog("Warning: countriesAltNamesFP property is not set in mdrmine project.xml, countries mapping will be worse");
        } else {
            if (!(new File(this.countriesAltNamesFP).isFile())) {
                this.writeLog("Warning: countries alternative names file does not exist (path tested: " + this.countriesAltNamesFP + " ), countries mapping will be worse");
            } else {
                altNames = new HashMap<String, List<String>>();

                FileReader in = new FileReader(this.countriesAltNamesFP);
                BufferedReader br = new BufferedReader(in);
        
                final CSVParser parser = new CSVParserBuilder()
                                                .withSeparator(',')
                                                .build();
                final CSVReader csvReader = new CSVReaderBuilder(br)
                                                    .withCSVParser(parser)
                                                    .build();
                
                boolean skipNext = false;
        
                csvReader.readNext();   // Skip headers
                String[] lineValues = csvReader.readNext();
        
                while (lineValues != null) {
                    // TODO: check if line values are not empty? (should not be)
                    if (!skipNext) {
                        String isoCode = lineValues[0].toLowerCase();
                        if (altNames.getOrDefault(isoCode, null) == null) {
                            altNames.put(isoCode, new ArrayList<String>());
                        }
                        altNames.get(isoCode).add(lineValues[1].toLowerCase());
                    } else {
                        skipNext = false;
                    }
                    try {
                        lineValues = csvReader.readNext();
                    } catch (CsvMalformedLineException e) {
                        this.writeLog("Found malformed line, skipping it: " + e);
                        lineValues = new String[0];
                        skipNext = true;
                    }
                }
        
                csvReader.close();
            }
        }

        return altNames;
    }

    /**
     * TODO
     */
    public void storeCountries() throws Exception {
        // Store countries (no duplicates)
        HashSet<String> seenIds = new HashSet<String>();
        for (Item country: this.countriesMap.values()) {
            if (!seenIds.contains(country.getIdentifier())) {
                store(country);
                seenIds.add(country.getIdentifier());
            }
        }
    }

    /**
     * TODO
     * 
     * 2-letter ISO code
     */
    public Item getCountry(String value) throws Exception {
        Item country = null;

        if (countriesMap.isEmpty()) {
            throw new RuntimeException("The list of Country items is empty, you likely forgot to call loadCountries() at the start of your parser");
        }

        if (!ConverterUtils.isNullOrEmptyOrBlank(value)) {
            value = value.toLowerCase();
            if (this.countriesMap.containsKey(value)) {
                country = this.countriesMap.get(value);
            } else {
                this.writeLog("Couldn't match country string '" + value + "' to a CV country");
            }
        }
        return country;
    }

    /**
     * TODO
     * 
     */
    public HashMap<String, IDsHandler> getExistingStudyIDs() throws Exception {
        HashMap<String, IDsHandler> idsMap = new HashMap<String, IDsHandler>();

        ClassDescriptor cdStudy = this.getModel().getClassDescriptorByName("Study");
        if (cdStudy == null) {
            throw new RuntimeException("This model does not contain a Study class");
        }

        Query q = new Query();
        QueryClass qcStudy = new QueryClass(cdStudy.getType());

        QueryField qfCtisID = new QueryField(qcStudy, "primaryIdentifier");
        QueryField qfNctID = new QueryField(qcStudy, "nctID");
        QueryField qfEuctrID = new QueryField(qcStudy, "euctrID");

        q.addFrom(qcStudy);
        q.addToSelect(qfCtisID);
        q.addToSelect(qfNctID);
        q.addToSelect(qfEuctrID);
        
        Results res = this.os.execute(q);
        Iterator<?> resIter = res.iterator();

        while (resIter.hasNext()) {
            ResultsRow<?> rr = (ResultsRow<?>) resIter.next();

            String ctisID = (String) rr.get(0);
            String nctID = (String) rr.get(1);
            String euctrID = (String) rr.get(2);

            IDsHandler l = new IDsHandler(this.logger, ctisID, nctID, euctrID);

            // Adding all combinations of entries in idsMap (1 per ID)
            if (!ConverterUtils.isNullOrEmptyOrBlank(ctisID)) {
                idsMap.put(ctisID, l);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(nctID)) {
                idsMap.put(nctID, l);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(euctrID)) {
                idsMap.put(euctrID, l);
            }
        }

        return idsMap;
    }

    /**
     * Create and store item (instance) of a class. Works for all classes except the Study and Country classes.
     * 
     * @param mainClassItem the already created item of the main class to reference (Study)
     * @param className the name of the class to create an item of
     * @param kv array of field name - field value pairs to set class item attribute values
     * @return the created item
     */
    public Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item item = this.createClassItem(mainClassItem, className, kv);
        this.storeClassItem(mainClassItem, item);

        return item;
    }

    /**
     * Create item (instance) of a class.
     * 
     * @param itemToReference an already created item to reference (e.g. Study)
     * @param className the name of the class to create an item of
     * @param kv array of field name - field value pairs to set class item attribute values
     * @return the created item
     */
    public Item createClassItem(Item itemToReference, String className, String[][] kv) throws Exception {
        Item classItem = createItem(className);

        // Set class values from fieldName - value pairs passed as argument
        for (int j = 0; j < kv.length; j++) {
            if (kv[j].length != 2) {
                throw new Exception("Key value tuple is not of length == 2");
            }
            classItem.setAttributeIfNotNull(kv[j][0], kv[j][1]);
        }

        if (classItem != null) {
            this.handleReferencesAndCollections(itemToReference, classItem);
        }

        return classItem;
    }

    /**
     * TODO
     * overload without any item to reference
     */
    public Item createClassItem(String className, String[][] kv) throws Exception {
        Item classItem = createItem(className);

        // Set class values from fieldName - value pairs passed as argument
        for (int j = 0; j < kv.length; j++) {
            if (kv[j].length != 2) {
                throw new Exception("Key value tuple is not of length == 2");
            }
            classItem.setAttributeIfNotNull(kv[j][0], kv[j][1]);
        }

        return classItem;
    }

    /**
     * TODO
     * Note: mainClassItem is required for overloading with CacheConverter
     */
    public void storeClassItem(Item mainClassItem, Item item) throws Exception {
        if (item != null) {
            store(item);
        }
    }

    /**
     * TODO
     * Items order shouldn't matter
     */
    public void handleReferencesAndCollections(Item itemA, Item itemB) throws Exception {
        if (itemA != null && itemB != null) {
            ReferenceDescriptor rdInAOfB = this.getReferenceDescriptorInItemAOfItemB(itemA, itemB);    // Can be a CollectionDescriptor
    
            // Reference in itemA to itemB
            if (rdInAOfB != null) {
                if (rdInAOfB.isCollection()) {
                    itemA.addToCollection(rdInAOfB.getName(), itemB);
                } else {
                    itemA.setReference(rdInAOfB.getName(), itemB);
                }
    
                // Reference in itemB to itemA
                ReferenceDescriptor rdInBOfA = rdInAOfB.getReverseReferenceDescriptor();
                if (rdInBOfA != null) {
                    if (rdInBOfA.isCollection()) {
                        itemB.addToCollection(rdInBOfA.getName(), itemA);
                    } else {
                        itemB.setReference(rdInBOfA.getName(), itemA);
                    }
                } else {
                    this.writeLog("handleReferencesAndCollections(): shouldn't happen");
                }
            } else {
                this.writeLog("handleReferencesAndCollections(): Failed to find reference in " + this.getClassDescriptor(itemA).getSimpleName()
                                + " class of " + this.getClassDescriptor(itemB).getSimpleName() + "class");
            }
        }
    }

    /**
     * TODO
     * Note: ReferenceDescriptor here also includes CollectionDescriptor sub-class
     */
    public ReferenceDescriptor getReferenceDescriptorInItemAOfItemB(Item itemA, Item itemB) throws Exception {
        ReferenceDescriptor foundRD = null;
        
        Set<ReferenceDescriptor> rds = Stream.concat(this.getClassDescriptor(itemA).getReferenceDescriptors().stream(), 
                                                    this.getClassDescriptor(itemA).getCollectionDescriptors().stream())
                                                    .collect(Collectors.toSet());
        Iterator<ReferenceDescriptor> rdsIter = rds.iterator();

        while (rdsIter.hasNext()) {
            ReferenceDescriptor rd = rdsIter.next();
            // Note: will not work as intended in case a Class has both a reference and a collection of the same Class
            if (rd.getReferencedClassDescriptor().equals(this.getClassDescriptor(itemB))) {
                foundRD = rd;
                break;
            }
        }

        return foundRD;
    }

    /**
     * TODO
     */
    public Item createAndStoreStudyIdentifier(Item study, String id, String identifierType, String identifierLink) throws Exception {
        Item studyIdentifier = null;

        if (!ConverterUtils.isNullOrEmptyOrBlank(id)) {
            this.createAndStoreClassItem(study, "StudyIdentifier", 
                new String[][]{{"identifierValue", id}, {"identifierType", identifierType},
                                {"identifierLink", identifierLink}});
        }

        return studyIdentifier;
    }

    /**
     * TODO
     */
    public Item createAndStoreObjectDate(Item dataObject, LocalDate date, String dateType) throws Exception {
        Item objectDate = null;

        objectDate = this.createAndStoreClassItem(dataObject, "ObjectDate", 
            new String[][]{{"dateType", dateType}, {"startDate", date != null ? date.toString() : null}});

        return objectDate;
    }

    /*
     * TODO
     */
    public Item createAndStoreStudyCountry(Item study, Item country, String countryStr, String status, 
        String plannedEnrolment, LocalDate cadDate, LocalDate ecdDate) throws Exception {

        Item studyCountry = this.createAndStoreClassItem(study, "StudyCountry", 
            new String[][]{{"countryName", WordUtils.capitalizeFully(countryStr, ' ', '-')},
                            {"status", status}, {"plannedEnrolment", plannedEnrolment},
                            {"compAuthorityDecisionDate", cadDate != null ? cadDate.toString() : null},
                            {"ethicsCommitteeDecisionDate", ecdDate != null ? ecdDate.toString() : null}});

        // Set references and collections between the Country and StudyCountry items and store the Country item
        if (country != null) {
            this.handleReferencesAndCollections(country, studyCountry);
            this.storeClassItem(study, country);
        }

        return studyCountry;
    }

    /**
     * TODO
     */
    public LocalDate parseDate(String dateStr, DateTimeFormatter df) {
        LocalDate date = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(dateStr)) {
            if (df != null) {
                date = ConverterUtils.getDateFromString(dateStr, df);
            }
            if (date == null) { // ISO format
                date = ConverterUtils.getDateFromString(dateStr, null);
                if (date == null) {   // d(d)/m(m)/yyyy
                    date = ConverterUtils.getDateFromString(dateStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
                    if (date == null) {   // dd month(word) yyyy
                        date = ConverterUtils.getDateFromString(dateStr, ConverterUtils.P_DATE_D_MWORD_Y_SPACES);
                        if (date == null) {
                            this.writeLog("parseDate(): couldn't parse date: " + dateStr);
                        }
                    }
                }
            }
        }

        return date;
    }
}
