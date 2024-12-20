package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024 MDRMine
 * Modified from 2002-2019 FlyMine 
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.Attribute;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.WordUtils;

import org.jsoup.Jsoup;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.exceptions.CsvMalformedLineException;



/**
 * Class to parse values from a WHO data file and store them as MDRMine items
 * @author ECRIN
 */
public class WhoConverter extends BioFileConverter
{
    /* Regex to Java converter: https://www.regexplanet.com/advanced/java/index.html */
    // TODO: "-"/"--" are none or unknown?
    private static final Pattern P_NOT_APPLICABLE = Pattern.compile(".*(not\\h*applicable|N/?A).*", Pattern.CASE_INSENSITIVE);  // N/A
    private static final Pattern P_NONE = Pattern.compile(".*\\b(none|no)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_UNKNOWN = Pattern.compile(".*\\b(unknown|not\\h*specified|not\\h*stated)\\b.*", Pattern.CASE_INSENSITIVE);
    // TODO: N/A != none?
    // TODO: "-" as no limit?
    private static final Pattern P_AGE_NOT_APPLICABLE = Pattern.compile(
        ".*(not\\h*applicable|N/?A|no\\h*limit|no|none|-2147483648).*", 
        Pattern.CASE_INSENSITIVE);  // N/A / No limit
    // [number][unit] possibly with gt/lt in front (gte/lte is not interesting here)
    private static final Pattern P_AGE = Pattern.compile(
        "[^0-9]*([<>][^=])?\\h*([0-9]+\\.?[0-9]*)\\h*(minute|hour|day|week|month|year|age)?.*", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TYPE_INTERVENTIONAL = Pattern.compile(
        ".*(BA\\/BE|intervention\\w*)\\b(?!.*observation).*", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TYPE_OBSERVATIONAL = Pattern.compile(
        ".*(PMS|factors|epidemio?logical|^observation\\w*)\\b(?!\\h+invasive).*", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TYPE_OTHER = Pattern.compile(".*(observational\\h+invasive|other\\w*).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TYPE_BASIC_SCIENCE = Pattern.compile(".*basic.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TYPE_EXPANDED_ACCESS = Pattern.compile(".*expanded.*", Pattern.CASE_INSENSITIVE);
    private static final String PHASE_NBS = "0|1|2|3|4|iv|iii|ii|i";  // No subroutines in Java Regex
    private static final Pattern P_PHASE_NUMBER = Pattern.compile(
        "^(Phase|Early\\h+phase)?[-|\\h]*(0|1|2|3|4|iv|iii|ii|i)(?>[^1234iv\\n]+\\b(0|1|2|3|4|iv|iii|ii|i))?.*", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PHASE_VERBOSE = Pattern.compile(
        ".*Phase\\h*i\\):\\h*(no|yes)?.*\\(Phase\\h*ii\\):\\h*(no|yes)?.*\\(Phase\\h*iii\\):\\h*(no|yes)?.*\\(Phase\\h*iv\\):\\h*(no|yes)?.*", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FEATURE_INTERVENTIONAL = Pattern.compile(
        "(allocation\\h*:\\h*([^.,;]*)([\\.,;]))?\\h*(intervention\\h* model\\h*:\\h*([^.,;]*)\\3)?\\h*(primary\\h*purpose\\h*:\\h*([^.,;]*)\\3)?\\h*(masking\\h*:\\h*(.*)\\3)?.*", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_GENDER_ALL = Pattern.compile("^\\h*(all|both|b).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_GENDER_STRUCTURED = Pattern.compile(".*(Female:\\h*(\\w+|)).*(Male:\\h*(\\w+|)).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_GENDER_MF = Pattern.compile("^\\h*((?:<br>\\h*)?\\b(male|men|m)\\w*\\b)(.*(female|women|f)\\w*\\b)?.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_GENDER_FM = Pattern.compile("^\\h*(\\b(female|women|f)\\w*\\b)(.*(male|men|m)\\w*\\b)?.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_IEC_NONE = Pattern.compile("^((?:inclusion|exclusion) criteria):\\h*(|no\\w*(?:\\h*exclusion\\h*criteria)?|\\/)\\.?$", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_IEC_NA = Pattern.compile("^((?:inclusion|exclusion) criteria):\\h*(not\\h*applicable|n\\/?a)\\.?$", Pattern.CASE_INSENSITIVE);
    /* Studies */
    private static final String NOT_APPLICABLE = "N/A";
    private static final String UNKNOWN = "Unknown";
    private static final String NONE = "None";
    private static final String NOT_STATED = "Not stated";
    private static final String TYPE_INTERVENTIONAL = "Interventional";
    private static final String TYPE_OBSERVATIONAL = "Observational";
    private static final String TYPE_BASIC_SCIENCE = "Basic science";
    private static final String TYPE_EXPANDED_ACCESS = "Expanded access";
    private static final String TYPE_OTHER = "Other";
    private static final String FEATURE_ALLOCATION = "Allocation";
    private static final String FEATURE_INTERVENTION_MODEL = "Intervention model";
    private static final String FEATURE_PRIMARY_PURPOSE = "Primary purpose";
    private static final String FEATURE_MASKING = "Masking";
    private static final String SPONSOR_NONE = "No sponsor";
    private static final String GENDER_ALL = "All";
    private static final String GENDER_WOMEN = "Female";
    private static final String GENDER_MEN = "Male";
    private static final String IC_PREFIX = "Inclusion criteria: ";
    private static final String EC_PREFIX = "Exclusion criteria: ";
    /* Objects */
    private static final String O_TYPE_TRIAL_REGISTRY_ENTRY = "Trial registry entry";
    private static final String O_CLASS_TEXT = "Text";
    private static final String CONTRIBUTOR_TYPE_SPONSOR = "Sponsor";
    private static final String CONTRIBUTOR_TYPE_SCIENTIFIC_SUPPORT = "Scientific support organisation";

    static final Map<String, String> PHASE_NUMBER_MAP = Map.of(
        "1", "1", 
        "2", "2", 
        "3", "3", 
        "4", "4", 
        "i", "1", 
        "ii", "2", 
        "iii", "3", 
        "iv", "4"
    );

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss.SSS");

    private static final String DATASET_TITLE = "ICTRPWeek22July2024";
    private static final String DATA_SOURCE_NAME = "WHO";

    private String headersFilePath = "";
    private String logDir = "";
    private Map<String, Integer> fieldsToInd;
    private Writer logWriter = null;
    private String trialID = null;  // Used for logging

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public WhoConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * Set headersFilePath from the corresponding source property in project.xml.
     * Method called by InterMine.
     * 
     * @param fp the path to the headers file
     */
    public void setHeadersFilePath(String fp) {
        this.headersFilePath = fp;
    }

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
     * Instantiate logger by creating log file and writer.
     * This sets the logWriter instance attribute.
     */
    public void startLogging() throws Exception {
        if (!this.logDir.equals("")) {
            String current_timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                
            Path logDir = Paths.get(this.logDir);
            if (!Files.exists(logDir)) Files.createDirectories(logDir);

            Path logFile = Paths.get(logDir.toString(), current_timestamp + "_who.log");
            this.logWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile.toString()), "utf-8"));
        } else {
            throw new Exception("Log folder not specified");
        }
    }

    /**
     * Close opened log writer.
     */
    public void stopLogging() throws IOException {
        if (this.logWriter != null) {
            this.logWriter.close();
        }
    }

    /**
     * Process WHO data file by iterating on each line of the data file.
     * Method called by InterMine.
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        this.startLogging();

        this.fieldsToInd = this.getHeaders();

        final CSVParser parser = new CSVParserBuilder()
                                        .withSeparator(',')
                                        .withQuoteChar('"')
                                        .build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                                            .withCSVParser(parser)
                                            .build();

        boolean skipNext = false;

        // nextLine[] is an array of values from the line
        String[] nextLine = csvReader.readNext();

        // TODO: performance tests? compared to iterator
        while (nextLine != null) {
            if (!skipNext) {
                this.storeValues(nextLine);
            } else {
                skipNext = false;
            }
            try {
                nextLine = csvReader.readNext();
            } catch (CsvMalformedLineException e) {
                nextLine = new String[0];
                skipNext = true;
            }
        }

        this.stopLogging();
        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    /**
     * Parse and store values as MDRMine items and attributes, from a list of values of a line of the data file.
     * 
     * @param lineValues the list of raw values of a line in the data file
     */
    public void storeValues(String[] lineValues) throws Exception {
        Item study = createItem("Study");
        // study.setAttribute("newField", "15");
        Item doRegistryEntry = createItem("DataObject");

        // TODO: something with last_update? is it trial start date?
        // TODO: do something with date registration / date enrolment?

        // TODO: skip creating study if ID is missing?

        /* ID and ID URL + DO */
        String trialID = this.getAndCleanValue(lineValues, "TrialID");
        this.trialID = trialID;
        if (!WhoConverter.isEmptyOrBlankOrNull(trialID)) {
            Item studyIdentifier = createItem("StudyIdentifier");
            studyIdentifier.setAttribute("identifierValue", trialID);
            studyIdentifier.setReference("study", study);

            /* Primary identifier URL */
            String url = this.getAndCleanValue(lineValues, "url");
            if (!WhoConverter.isEmptyOrBlankOrNull(url)) {
                studyIdentifier.setAttribute("identifierLink", url);

                doRegistryEntry.setAttribute("objectType", O_TYPE_TRIAL_REGISTRY_ENTRY);
                doRegistryEntry.setAttribute("objectClass", O_CLASS_TEXT);
                Item doInst = createItem("ObjectInstance");
                doInst.setAttribute("url", url);
                doInst.setReference("dataObject", doRegistryEntry);
                store(doInst);
                store(doRegistryEntry);
                doRegistryEntry.addToCollection("objectInstances", doInst);
                // TODO: resource type?
            }
            store(studyIdentifier);
            study.addToCollection("studyIdentifiers", studyIdentifier);
            // TODO: identifier type
            // TODO: date?
        }

        /* Secondary IDs */
        String secondaryIDs = this.getAndCleanValue(lineValues, "SecondaryIDs");
        this.parseSecondaryIDs(study, secondaryIDs);

        /* Public title */
        String publicTitle = this.getAndCleanValue(lineValues, "public_title");
        if (!WhoConverter.isEmptyOrBlankOrNull(publicTitle)) {
            Item studyTitle = createItem("StudyTitle");
            study.setAttribute("displayTitle", publicTitle);

            studyTitle.setAttribute("titleType", "Public title");
            studyTitle.setAttribute("titleText", publicTitle);
            studyTitle.setReference("study", study);
            store(studyTitle);
            study.addToCollection("studyTitles", studyTitle);
        } else {
            study.setAttribute("displayTitle", "Unknown study title");
        }

        /* Scientific title */
        String scientificTitle = this.getAndCleanValue(lineValues, "Scientific_title");
        if (!WhoConverter.isEmptyOrBlankOrNull(scientificTitle)) {
            Item studyTitle = createItem("StudyTitle");
            studyTitle.setAttribute("titleType", "Scientific Title");
            studyTitle.setAttribute("titleText", scientificTitle);
            studyTitle.setReference("study", study);
            store(studyTitle);
            study.addToCollection("studyTitles", studyTitle);
        }

        // TODO: not working as intended
        Item studySource = createItem("StudySource");
        studySource.setAttribute("sourceName", "WHO");
        studySource.setReference("study", study);
        store(studySource);
        study.addToCollection("studySources", studySource);

        /* Study people (public and scientific contacts) */
        this.parseContact(study, lineValues, "public");
        this.parseContact(study, lineValues, "scientific");

        /* Study type */
        this.parseStudyType(study, this.getAndCleanValue(lineValues, "study_type"));

        String studyDesign = this.getAndCleanValue(lineValues, "study_design");
        String phase = this.getAndCleanValue(lineValues, "phase");

        /* Study features (incl. phase) */
        this.parseStudyFeatures(study, this.getAndCleanValue(lineValues, "study_design"),
                                this.getAndCleanValue(lineValues, "phase"));

        /* Brief description 1/3 (study design) */
        // Note: using the fields currently used by the MDR to construct the briefDescription value
        // TODO: change/improve this field?
        this.addToBriefDescription(study, studyDesign);

        /* Study enrolment */
        String studyEnrolment = this.getAndCleanValue(lineValues, "Target_size");
        if (!WhoConverter.isEmptyOrBlankOrNull(studyEnrolment)) {
            try {
                // Note: setAttribute() only accepts String, value is then automatically converted to the type in the xml model, 
                // but we do have to check that it is actually a number
                if (isPosWholeNumber(studyEnrolment)) {
                    study.setAttribute("studyEnrolment", studyEnrolment);
                }
            } catch (NumberFormatException e) {
                this.writeLog("Couldn't cast to int this study enrolment value: " + studyEnrolment);
            }
        }

        /* Study status */
        // TODO: normalise values
        String studyStatus = this.getAndCleanValue(lineValues, "Recruitment_status");
        if (!WhoConverter.isEmptyOrBlankOrNull(studyStatus)) {
            study.setAttribute("studyStatus", studyStatus);
        }

        /* Study people: sponsors */
        String primarySponsor = this.getAndCleanValue(lineValues, "Primary_sponsor");
        String secondarySponsors = this.getAndCleanValue(lineValues, "Secondary_sponsors");
        // TODO: what is source support?
        String sourceSupport = this.getAndCleanValue(lineValues, "Source_Support");

        /* Primary and secondary sponsors, scientific support organisation (source support)*/
        this.createAndStoreStudyPeople(study, primarySponsor, CONTRIBUTOR_TYPE_SPONSOR);
        // TODO: there may be multiple sponsors in this field
        this.createAndStoreStudyPeople(study, secondarySponsors, CONTRIBUTOR_TYPE_SPONSOR);
        // Checking if the source support string is different than the primary and secondary sponsors + is not "please refer to primary and secondary sponsors"
        if (!sourceSupport.toLowerCase().contains("please") && !sourceSupport.equalsIgnoreCase(primarySponsor) && !sourceSupport.equalsIgnoreCase(secondarySponsors)) {
            this.createAndStoreStudyPeople(study, sourceSupport, CONTRIBUTOR_TYPE_SCIENTIFIC_SUPPORT);
        }

        /* Study countries */
        // TODO: reference country CV object + normalise values
        // TODO: parse few values where multiple-country delimiter is comma instead of semi-colon
        String countries = this.getAndCleanValue(lineValues, "Countries");
        if (!WhoConverter.isEmptyOrBlankOrNull(countries)) {
            if (countries.contains(";")) {
                String[] countriesList = countries.split(";");
                for (String country: countriesList) {
                    if (!WhoConverter.isEmptyOrBlankOrNull(country)) {
                        this.createAndStoreCountry(study, country);
                    }
                }
            } else {
                this.createAndStoreCountry(study, countries);
            }
        }

        /* Study conditions */
        // TODO: match values with CT codes/ICD Codes
        String conditions = this.getAndCleanValue(lineValues, "Conditions");
        if (!WhoConverter.isEmptyOrBlankOrNull(conditions)) {
            if (conditions.contains(";")) {
                String[] conditionsList = conditions.split(";");
                for (String condition: conditionsList) {
                    if (!WhoConverter.isEmptyOrBlankOrNull(condition)) {
                        this.createAndStoreCondition(study, condition);
                    }
                }
            } else {
                this.createAndStoreCondition(study, conditions);
            }
        }

        /* Interventions */
        String interventions = this.getAndCleanValue(lineValues, "Interventions");
        if (!WhoConverter.isEmptyOrBlankOrNull(interventions)) {
            study.setAttribute("interventions", interventions);
        }

        /* Brief description 2/3 (interventions) */
        this.addToBriefDescription(study, interventions);

        /* Min age */
        this.parseAgeField(this.getAndCleanValue(lineValues, "Agemin"), "minAge", "minAgeUnit", study);
        /* Max age */
        this.parseAgeField(this.getAndCleanValue(lineValues, "Agemax"), "maxAge", "maxAgeUnit", study);

        /* Gender */
        String gender = this.getAndCleanValue(lineValues, "Gender");
        if (!WhoConverter.isEmptyOrBlankOrNull(gender)) {
            this.parseGender(study, gender);
        }

        /* IEC */
        String ic = this.getAndCleanValue(lineValues, "Inclusion_Criteria");
        String ec = this.getAndCleanValue(lineValues, "Exclusion_Criteria");
        this.parseIEC(study, ic, ec);

        /* Primary outcome */
        // TODO: handle main empty values
        String primaryOutcome = this.getAndCleanValue(lineValues, "Primary_Outcome");
        if (!WhoConverter.isEmptyOrBlankOrNull(primaryOutcome)) {
            study.setAttribute("primaryOutcome", primaryOutcome);
        }

        /* Brief description 3/3 (primary outcome) */
        this.addToBriefDescription(study, primaryOutcome);

        /* Secondary outcomes */
        // TODO: handle main empty values + N/A
        String secondaryOutcomes = this.getAndCleanValue(lineValues, "Secondary_Outcomes");
        if (!WhoConverter.isEmptyOrBlankOrNull(secondaryOutcomes)) {
            study.setAttribute("secondaryOutcomes", secondaryOutcomes);
        }

        String bridgingFlag = this.getAndCleanValue(lineValues, "Bridging_flag");
        String bridgedType = this.getAndCleanValue(lineValues, "Bridged_type");
        String childs = this.getAndCleanValue(lineValues, "Childs");
        if (!WhoConverter.isEmptyOrBlankOrNull(bridgingFlag)) {
            study.setAttribute("testField1", bridgingFlag);
        }
        if (!WhoConverter.isEmptyOrBlankOrNull(bridgedType)) {
            study.setAttribute("testField2", bridgedType);
        }
        if (!WhoConverter.isEmptyOrBlankOrNull(childs)) {
            study.setAttribute("testField3", childs);
        }

        store(study);
    }

    /**
     * Concatenate text on a new line to study brief description field value.
     * 
     * @param study the study item to modify the brief description field of
     * @param text the text to concatenate (or set, if the field's value is empty) to the study's brief description
     */
    public void addToBriefDescription(Item study, String text) {
        if (!WhoConverter.isEmptyOrBlankOrNull(text)) {
            Attribute briefDescription = study.getAttribute("briefDescription");
            if (briefDescription != null) {
                String currentDesc = briefDescription.getValue();
                if (!WhoConverter.isEmptyOrBlankOrNull(currentDesc)) {
                    study.setAttribute("briefDescription", currentDesc + "\n" + text);
                } else {
                    study.setAttribute("briefDescription", text);
                }
            }
        }
    }

    /**
     * Parse secondary IDs input to create StudyIdentifier items.
     * 
     * @param study the study item to link to study identifiers
     * @param secIDsStr the input secondary IDs string
     */
    public void parseSecondaryIDs(Item study, String secIDsStr) {
        if (!WhoConverter.isEmptyOrBlankOrNull(secIDsStr)) {
            String[] ids = secIDsStr.split(";");
            for (String id: ids) {
                if (!WhoConverter.isEmptyOrBlankOrNull(id)) {
                    Item studyIdentifier = createItem("StudyIdentifier");
                    studyIdentifier.setAttribute("identifierValue", id);
                    studyIdentifier.setReference("study", study);
                    study.addToCollection("studyIdentifiers", studyIdentifier);
                }
                // TODO: identifier type
                // TODO: identifier link
            }
        }
    }

    /**
     * Parse Public_Contact or Scientific_Contact input fields to create StudyPeople items.
     * 
     * @param study the study item to link to study people
     * @param lineValues the list of all values for a line in the data file
     * @param contactType public or scientific contact
     */
    public void parseContact(Item study, String[] lineValues, String contactType) throws Exception {
        String[] firstNames = {};
        String[] lastNames = {};
        String[] affiliations = {};

        String fieldPrefix = "";
        if (contactType.equalsIgnoreCase("public")) {
            fieldPrefix = "Public_";
        } else if (contactType.equalsIgnoreCase("scientific")) {
            fieldPrefix = "Scientific_";
        } else {
            throw new Exception("Unknown value \"" + contactType + "\" for contactType arg of parseContact()");
        }
        
        String firstNamesString = this.getAndCleanValueNoStrip(lineValues, (fieldPrefix + "Contact_Firstname"));
        if (!WhoConverter.isEmptyOrBlankOrNull(firstNamesString)) {
            firstNames = firstNamesString.split(";");
        }

        String lastNamesString = this.getAndCleanValueNoStrip(lineValues, (fieldPrefix + "Contact_Lastname"));
        if (!WhoConverter.isEmptyOrBlankOrNull(lastNamesString)) {
            lastNames = lastNamesString.split(";");
        }

        String affiliationsString = this.getAndCleanValueNoStrip(lineValues, (fieldPrefix + "Contact_Affiliation"));
        if (!WhoConverter.isEmptyOrBlankOrNull(affiliationsString)) {
            affiliations = affiliationsString.split(";");
        }

        /* Address and phone strings present in WHO are unused here since they don't appear in our model */
        // Using the field with the most semi-colon-separated elements for iterating
        int maxLen = Math.max(Math.max(firstNames.length, lastNames.length), affiliations.length);

        String firstName, lastName, affiliation;
        Item sp;

        if (maxLen > 0) {
            for (int i = 0; i < maxLen; i++) {
                sp = createItem("StudyPeople");
                firstName = "";
                lastName = "";
                affiliation = "";

                // TODO: possible that the string ends with both semi-colons?
                
                // First name
                if (!WhoConverter.isEmptyOrBlankOrNull(firstNamesString)) {
                    if (firstNamesString.endsWith(";") && i < firstNames.length) {
                        firstName = firstNames[i];
                    } else if (firstNames.length + i - maxLen >= 0) {
                        // If the string starts with a semi-colon or not, the indexing is the same as length == maxLen if there is no semi-colon
                        firstName = firstNames[firstNames.length + i - maxLen].strip();
                    } else {
                        this.writeLog("parseContact(): line parsing error, firstName values list is missing a value");
                    }
                }

                // Last name
                if (!WhoConverter.isEmptyOrBlankOrNull(lastNamesString)) {
                    if (lastNamesString.endsWith(";") && i < lastNames.length) {
                        lastName = lastNames[i];
                    } else if (lastNames.length + i - maxLen >= 0) {
                        // If the string starts with a semi-colon or not, the indexing is the same as length == maxLen if there is no semi-colon
                        lastName = lastNames[lastNames.length + i - maxLen].strip();
                    } else {
                        this.writeLog("parseContact(): line parsing error, lastName values list is missing a value");
                    }
                }

                // Affiliation (raw value from data)
                if (!WhoConverter.isEmptyOrBlankOrNull(affiliationsString)) {
                    if (affiliationsString.endsWith(";") && i < affiliations.length) {
                        affiliation = affiliations[i];
                    } else if (affiliations.length + i - maxLen >= 0) {
                        // If the string starts with a semi-colon or not, the indexing is the same as length == maxLen if there is no semi-colon
                        affiliation = affiliations[affiliations.length + i - maxLen].strip();
                    } else {
                        this.writeLog("parseContact(): line parsing error, affiliation values list is missing a value");
                    }
                }

                // Setting the values
                this.setPeopleValues(sp, firstName, lastName, affiliation, contactType);

                // TODO: handle affiliation differently? (same for multiple people) NCT04163835
                // TODO: how to avoid duplicates that are not really duplicates? NCT04163835
                // TODO: also set organisation field?
                // Check MDR code for this: https://github.com/scanhamman/MDR_Harvester/blob/af313e05f60012df56c8a6dd3cbb73a9fe1cd906/GeneralHelpers/StringFunctions.cs#L947

                sp.setReference("study", study);
                store(sp);
                study.addToCollection("studyPeople", sp);
            }
        }
    }

    /**
     * Set values for various person fields of a Study People instance.
     * 
     * @param studyPeople the studyPeople instance to set fields of
     * @param firstName the first name value
     * @param lastName the last name value
     * @param affiliation the affiliation value
     * @param contactType public or scientific contact
     */
    public void setPeopleValues(Item studyPeople, String firstName, String lastName, String affiliation, String contactType) {
        // TODO: attempt at separating first name/last name if one of firstName/lastName is empty?
        // Check MDR code for this: https://github.com/scanhamman/MDR_Harvester/blob/master/GeneralHelpers/StringFunctions.cs#L714
        if (!WhoConverter.isEmptyOrBlankOrNull(firstName) && !WhoConverter.isEmptyOrBlankOrNull(lastName)) {
            studyPeople.setAttribute("personGivenName", firstName);
            studyPeople.setAttribute("personFamilyName", lastName);
            studyPeople.setAttribute("personFullName", (firstName + " " + lastName));
        } else if (!WhoConverter.isEmptyOrBlankOrNull(firstName)) {
            studyPeople.setAttribute("personFullName", firstName);
        } else if (!WhoConverter.isEmptyOrBlankOrNull(lastName)) {
            studyPeople.setAttribute("personFullName", lastName);
        }
        
        if (!WhoConverter.isEmptyOrBlankOrNull(affiliation)) {
            studyPeople.setAttribute("personAffiliation", affiliation);
        }

        // TODO: normalise against MDR CV?
        // TODO: values to constant
        if (contactType.equalsIgnoreCase("public")) {
            studyPeople.setAttribute("contribType", "Public contact");
        } else {
            // Exception already thrown earlier so value can't be anything other than "scientific"
            studyPeople.setAttribute("contribType", "Scientific contact");
        }
    }

    /**
     * Parse min/max age value to set age value and unit fields.
     * 
     * @param ageStr the age string
     * @param ageAttr the age attribute name to set (either minAge or maxAge)
     * @param unitAttr the unit attribute name to set (either minAgeUnit or maxAgeUnit)
     * @param study the study item containing the attributes to set
     */
    public void parseAgeField(String ageStr, String ageAttr, String unitAttr, Item study) {
        if (!WhoConverter.isEmptyOrBlankOrNull(ageStr)) {
            // Check for N/A or no limit
            Matcher mAgeNotApplicable = P_AGE_NOT_APPLICABLE.matcher(ageStr);
            if (mAgeNotApplicable.matches()) {
                study.setAttribute(ageAttr, NOT_APPLICABLE);
            } else {    // Not stated
                Matcher mAgeNotStated = P_UNKNOWN.matcher(ageStr);
                if (mAgeNotStated.matches()) {
                    study.setAttribute(ageAttr, NOT_STATED);
                } else {
                    Matcher mAge = P_AGE.matcher(ageStr);
                    boolean successful = false;
                    if (mAge.matches()) {
                        String g1 = mAge.group(1);  // GT or LT
                        String g2 = mAge.group(2);  // age value
                        String g3 = mAge.group(3);  // age unit
                        if (!(g2 == null)) {
                            if (NumberUtils.isParsable(g2)) {
                                if (NumberUtils.isDigits(g2)) {
                                    int ageNumber = Integer.parseInt(g2);

                                    if (g1 != null) {    // GT/LT
                                        if (ageAttr.equalsIgnoreCase("minage")) {
                                            if (g1.equals(">")) {
                                                ageNumber++;
                                            } else {
                                                this.writeLog("Wrong inequality sign for minAgeUnit: "
                                                                + g1 + " full string: " + ageStr);
                                            }
                                        } else if (ageAttr.equalsIgnoreCase("maxage")) {
                                            if (g1.equals("<")) {
                                                ageNumber--;
                                            } else {
                                                this.writeLog("Wrong inequality sign for maxAgeUnit: "
                                                                + g1 + " full string: " + ageStr);
                                            }
                                        }
                                    }

                                    study.setAttribute(ageAttr, String.valueOf(ageNumber));
                                } else {    // Case where min age value is float
                                    study.setAttribute(ageAttr, g2);
                                }

                                // TODO: how to check unit against data?
                                if (!(g3 == null || g3.equalsIgnoreCase("age"))) {
                                    study.setAttribute(unitAttr, WhoConverter.normaliseUnit(g3));
                                } else {    // If no unit (or unit is "age"), we assume it's years
                                    study.setAttribute(unitAttr, "Years");
                                }

                                successful = true;
                            } else {
                                this.writeLog("Wrong format minAge value: " 
                                                + g2 + " full string: " + ageStr);
                            }
                        }
                        if (!successful) {
                            this.writeLog("Couldn't parse " + ageAttr + " and " + unitAttr + " properly, string: " + ageStr + 
                                ", parsed groups: -g1: " + g1 + " -g2: " + g2 + " -g3: " + g3);
                        }
                    } else {
                        this.writeLog("Couldn't parse " + ageAttr + " and " + unitAttr + " properly, string: " + ageStr + 
                            ", no matches found");
                    }
                }
            }
        }
    }

    /**
     * Parse study type input to set value of study type field.
     * 
     * @param study the study item to set the study type value of
     * @param studyTypeStr the input study type string
     */
    public void parseStudyType(Item study, String studyTypeStr) {
        if (!WhoConverter.isEmptyOrBlankOrNull(studyTypeStr)) {
            study.setAttribute("studyStatus", studyTypeStr);
            Matcher mTypeInterventional = P_TYPE_INTERVENTIONAL.matcher(studyTypeStr);
            if (mTypeInterventional.matches()) {    // Interventional
                study.setAttribute("studyType", TYPE_INTERVENTIONAL);
            } else {    // Observational
                Matcher mTypeObservational = P_TYPE_OBSERVATIONAL.matcher(studyTypeStr);
                if (mTypeObservational.matches()) {
                    study.setAttribute("studyType", TYPE_OBSERVATIONAL);
                } else {    // Other
                    Matcher mTypeOther = P_TYPE_OTHER.matcher(studyTypeStr);
                    if (mTypeOther.matches()) {
                        study.setAttribute("studyType", TYPE_OTHER);
                    } else {    // Basic science
                        Matcher mTypeBasicScience = P_TYPE_BASIC_SCIENCE.matcher(studyTypeStr);
                        if (mTypeBasicScience.matches()) {
                            study.setAttribute("studyType", TYPE_BASIC_SCIENCE);
                        } else {    // N/A
                            Matcher mTypeNA = P_NOT_APPLICABLE.matcher(studyTypeStr);
                            if (mTypeNA.matches()) {
                                study.setAttribute("studyType", NOT_APPLICABLE);
                            } else {    // Expanded access
                                Matcher mTypeExpandedAcess = P_TYPE_EXPANDED_ACCESS.matcher(studyTypeStr);
                                if (mTypeExpandedAcess.matches()) {
                                    study.setAttribute("studyType", TYPE_EXPANDED_ACCESS);
                                } else {    // Unknown
                                    study.setAttribute("studyType", UNKNOWN);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Parse phase and study features input to create StudyFeature items.
     * 
     * @param study the study item to link to study features
     * @param featuresStr the input study features string
     * @param phaseStr the input phase string
     */
    public void parseStudyFeatures(Item study, String featuresStr, String phaseStr) throws Exception {
        if (!WhoConverter.isEmptyOrBlankOrNull(phaseStr)) {
            Item phaseFeature = createItem("StudyFeature");
            phaseFeature.setAttribute("featureType", "Phase");

            Matcher mPhaseNumber = P_PHASE_NUMBER.matcher(phaseStr);
            if (mPhaseNumber.matches()) {   // "Numbers" match
                String phase = mPhaseNumber.group(1);
                String nb1 = mPhaseNumber.group(2);
                String nb2 = mPhaseNumber.group(3);
                if (nb1 != null) {
                    if ((phase != null && phase.toLowerCase().contains("early")) || nb1.equals("0")) {  // Early phase 1
                        phaseFeature.setAttribute("featureValue", "Early phase 1");
                        if (nb2 != null) {
                            this.writeLog("Anomaly: second number matched for early phase string, phase: " 
                                            + phase + "; nb1: " + nb1 + "; nb2: " + nb2 + ", full string: " + phaseStr);
                        }
                    } else if (nb2 != null && !nb1.equalsIgnoreCase(nb2)) {   // Two phases
                        phaseFeature.setAttribute("featureValue", "Phase " + WhoConverter.convertPhaseNumber(nb1) + "/" + WhoConverter.convertPhaseNumber(nb2));
                    } else {    // One phase
                        phaseFeature.setAttribute("featureValue", "Phase " + WhoConverter.convertPhaseNumber(nb1));
                    }
                } else {
                    this.writeLog("Anomaly: matched \"numbers\" phase string but no phase number, phase: " 
                                    + phase + "; nb1: " + nb1 + "; nb2: " + nb2 + ", full string: " + phaseStr);
                }
            } else {
                Matcher mPhaseVerbose = P_PHASE_VERBOSE.matcher(phaseStr);
                if (mPhaseVerbose.matches()) {    // "Verbose" match
                    String p1 = mPhaseVerbose.group(1);
                    String p2 = mPhaseVerbose.group(2);
                    String p3 = mPhaseVerbose.group(3);
                    String p4 = mPhaseVerbose.group(4);
                    if (p1 != null || p2 != null || p3 != null || p4 != null) {
                        // Getting group indices where group is yes
                        String[] phasesGroups = {p1, p2, p3, p4};
                        ArrayList<Integer> phasesRes = new ArrayList<Integer>();
                        for (int i = 1; i <= phasesGroups.length; i++) {
                            if (phasesGroups[i-1] != null && phasesGroups[i-1].equalsIgnoreCase("yes")) {
                                phasesRes.add(i);
                            }
                        }

                        if (phasesRes.size() > 0) {
                            if (phasesRes.size() == 1) {    // One phase
                                phaseFeature.setAttribute("featureValue", "Phase " + String.valueOf(phasesRes.get(0)));
                            } else if (phasesRes.size() == 2) { // Two phases
                                phaseFeature.setAttribute("featureValue", "Phase " + String.valueOf(phasesRes.get(0)) + "/" + String.valueOf(phasesRes.get(1)));
                            } else {
                                this.writeLog("Anomaly: matched more than 2 groups for \"verbose\" phase field, g1: " + p1 + "; g2: " + p2 + "; g3: " + p3 + ", full string: " + phaseStr);
                            }
                        } else {
                            this.writeLog("Anomaly: matched but couldn't properly parse \"verbose\" phase field, g1: " + p1 + "; g2: " + p2 + "; g3: " + p3 + ", full string: " + phaseStr);
                        }
                    } else {
                        this.writeLog("Anomaly: matched but couldn't properly parse \"verbose\" phase field, g1: " + p1 + "; g2: " + p2 + "; g3: " + p3 + ", full string: " + phaseStr);
                    }
                } else {
                    Matcher mPhaseNA = P_NOT_APPLICABLE.matcher(phaseStr);
                    if (mPhaseNA.matches()) {   // N/A
                        phaseFeature.setAttribute("featureValue", NOT_APPLICABLE);
                    } else {    // Using raw value
                        phaseFeature.setAttribute("featureValue", phaseStr);
                    }
                }
            }

            phaseFeature.setReference("study", study);
            store(phaseFeature);
            study.addToCollection("studyFeatures", phaseFeature);
        }

        if (!WhoConverter.isEmptyOrBlankOrNull(featuresStr)) {
            // TODO: improve parsing of study features, currently it works only for most interventional studies
            Matcher mFeatureInterventional = P_FEATURE_INTERVENTIONAL.matcher(featuresStr);
            if (mFeatureInterventional.matches()) { // Interventional features
                String allocation = mFeatureInterventional.group(2);
                String model = mFeatureInterventional.group(5);
                String purpose = mFeatureInterventional.group(7);
                String masking = mFeatureInterventional.group(9);
                this.createAndStoreFeature(study, FEATURE_ALLOCATION, allocation);
                this.createAndStoreFeature(study, FEATURE_INTERVENTION_MODEL, model);
                this.createAndStoreFeature(study, FEATURE_PRIMARY_PURPOSE, purpose);
                this.createAndStoreFeature(study, FEATURE_MASKING, masking);
            } else {    // Using raw value
                this.createAndStoreFeature(study, "", featuresStr);
            }
        }
    }

    /**
     * TODO
     */
    public boolean createAndStoreFeature(Item study, String featureType, String featureValue) throws Exception {
        boolean success = false;
        if (!WhoConverter.isEmptyOrBlankOrNull(featureValue)) {
            Item studyFeature = createItem("StudyFeature");
            if (!WhoConverter.isEmptyOrBlankOrNull(featureType)) {
                studyFeature.setAttribute("featureType", featureType);
            }
            studyFeature.setAttribute("featureValue", featureValue);
            studyFeature.setReference("study", study);
            store(studyFeature);
            study.addToCollection("studyFeatures", studyFeature);
            success = true;
        }
        return success;
    }

    /**
     * TODO
     */
    public void createAndStoreStudyPeople(Item study, String studyPeopleStr, String contribType) throws Exception {
        if (!WhoConverter.isEmptyOrBlankOrNull(studyPeopleStr)) {
            boolean store = true;
            Item studyPeople = createItem("StudyPeople");
            studyPeople.setAttribute("contribType", contribType);

            Matcher mStudyPeopleNA = P_NOT_APPLICABLE.matcher(studyPeopleStr);
            if (mStudyPeopleNA.matches()) {    // N/A
                studyPeople.setAttribute("personFullName", NOT_APPLICABLE);
                studyPeople.setAttribute("organisationName", NOT_APPLICABLE);
            } else {    // No sponsor
                Matcher mStudyPeopleNone = P_NONE.matcher(studyPeopleStr);
                if (mStudyPeopleNone.matches()) {
                    if (contribType.equals(CONTRIBUTOR_TYPE_SPONSOR)) {
                        studyPeople.setAttribute("personFullName", SPONSOR_NONE);
                        studyPeople.setAttribute("organisationName", SPONSOR_NONE);
                    } else {
                        // Not storing "None" scientific support organisations
                        store = false;
                    }
                } else {    // Using raw value
                    if (contribType.equals(CONTRIBUTOR_TYPE_SCIENTIFIC_SUPPORT)) {
                        studyPeople.setAttribute("organisationName", studyPeopleStr);
                    } else {
                        // TODO: setting both for now, need logic to distinguish people from orgs? -> check MDR
                        studyPeople.setAttribute("organisationName", studyPeopleStr);
                        studyPeople.setAttribute("personFullName", studyPeopleStr);
                    }
                }
            }

            if (store) {
                studyPeople.setReference("study", study);
                store(studyPeople);
                study.addToCollection("studyPeople", studyPeople);
            }
        }
    }

    /**
     * TODO
     */
    public boolean createAndStoreCountry(Item study, String countryStr) throws Exception {
        boolean success = false;
        if (!WhoConverter.isEmptyOrBlankOrNull(countryStr)) {
            Item country = createItem("StudyCountry");
            // Don't categorise prepositions?
            country.setAttribute("countryName", WordUtils.capitalizeFully(countryStr, ' ', '-'));

            country.setReference("study", study);
            store(country);
            study.addToCollection("studyCountries", country);
            success = false;
        }
        return success;
    }

    /**
     * TODO
     */
    public boolean createAndStoreCondition(Item study, String conditionStr) throws Exception {
        boolean success = false;
        if (!WhoConverter.isEmptyOrBlankOrNull(conditionStr)) {
            Item condition = createItem("StudyCondition");
            condition.setAttribute("originalValue", WordUtils.capitalizeFully(conditionStr, ' ', '-'));

            condition.setReference("study", study);
            store(condition);
            study.addToCollection("studyConditions", condition);
            success = false;
        }
        return success;
    }

    /**
     * TODO
     */
    public void parseGender(Item study, String genderStr) {
        Matcher mGenderAll = P_GENDER_ALL.matcher(genderStr);
        if (mGenderAll.matches()) {
            study.setAttribute("studyGenderElig", GENDER_ALL);
        } else {    // "structured" pattern

            Matcher mGenderStructured = P_GENDER_STRUCTURED.matcher(genderStr);
            if (mGenderStructured.matches()) {
                this.setGenderFromMatches(study, mGenderStructured, genderStr, GENDER_WOMEN, GENDER_MEN, true);
            } else {    // "Non-structured" pattern

                Matcher mGenderMF = P_GENDER_MF.matcher(genderStr);
                if (mGenderMF.matches()) {
                    this.setGenderFromMatches(study, mGenderMF, genderStr, GENDER_MEN, GENDER_WOMEN, false);
                } else {

                    Matcher mGenderFM = P_GENDER_FM.matcher(genderStr);
                    if (mGenderFM.matches()) {
                        this.setGenderFromMatches(study, mGenderFM, genderStr, GENDER_WOMEN, GENDER_MEN, false);
                    } else {    // Not specified
                    
                        Matcher mGenderNotSpecified = P_UNKNOWN.matcher(genderStr);
                        if (mGenderNotSpecified.matches()) {
                            study.setAttribute("studyGenderElig", UNKNOWN);
                        } else {    // Raw value (shouldn't happen)

                            study.setAttribute("studyGenderElig", genderStr);
                            this.writeLog("Couldn't parse study gender (used raw value): " + genderStr);
                        }
                    }
                }
            }
        }
    }

    /**
     * Set study gender field using regex group matches.
     * 
     * @param study the study item to set the gender field of
     * @param m the regex matcher with the groups
     * @param genderStr the gender input string
     * @param gender1 the value to set if (only) the first group matches
     * @param gender2 the value to set if (only) the second group matches
     * @param structured a boolean indicating if the regex used was structured (P_GENDER_STRUCTURED) or not (P_GENDER_MF, P_GENDER_FM)
     */
    public void setGenderFromMatches(Item study, Matcher m, String genderStr, String gender1, String gender2, boolean structured) {
        String g1 = m.group(2);
        String g2 = m.group(4);
        if (g1 != null && g2 != null && (!structured || (g1.equalsIgnoreCase("yes") && g2.equalsIgnoreCase("yes")))) {
            study.setAttribute("studyGenderElig", GENDER_ALL);
        } else if (g1 != null && (!structured || g1.equalsIgnoreCase("yes"))) {
            study.setAttribute("studyGenderElig", gender1);
        } else if (g2 != null && (!structured || g2.equalsIgnoreCase("yes"))) {
            study.setAttribute("studyGenderElig", gender2);
        } else {
            this.writeLog("Match for study gender (structured regex) but no gender found, string: " + genderStr);
        }
    }

    /**
     * TODO
     */
    public void parseIEC(Item study, String icStr, String ecStr) {
        StringBuilder iec = new StringBuilder();

        /* Inclusion criteria */
        if (!WhoConverter.isEmptyOrBlankOrNull(icStr)) {    // None
            Matcher mIcNone = P_IEC_NONE.matcher(icStr);
            if (mIcNone.matches()) {
                String g1None = mIcNone.group(2);
                if (!WhoConverter.isEmptyOrBlankOrNull(g1None)) {
                    iec.append(IC_PREFIX + NONE);
                } else {
                    iec.append(IC_PREFIX + UNKNOWN);
                }
            } else {    // N/A
                Matcher mIcNA = P_IEC_NA.matcher(icStr);
                if (mIcNA.matches()) {
                    iec.append(IC_PREFIX + NOT_APPLICABLE);
                } else {    // Raw value
                    iec.append(icStr);
                }
            }
        }

        /* Exclusion criteria */
        if (!WhoConverter.isEmptyOrBlankOrNull(ecStr)) {    // None
            Matcher mEcNone = P_IEC_NONE.matcher(ecStr);

            String adaptedEcPrefix = "";
            if (iec.length() > 0) {
                adaptedEcPrefix = ". " + EC_PREFIX;
            } else {
                adaptedEcPrefix = EC_PREFIX;
            }

            if (mEcNone.matches()) {
                String g2None = mEcNone.group(2);
                if (!WhoConverter.isEmptyOrBlankOrNull(g2None)) {
                    iec.append(adaptedEcPrefix + NONE);
                } else {
                    iec.append(adaptedEcPrefix + UNKNOWN);
                }
            } else {    // N/A
                Matcher mEcNA = P_IEC_NA.matcher(ecStr);
                if (mEcNA.matches()) {
                    iec.append(adaptedEcPrefix + NOT_APPLICABLE);
                } else {    // Raw value
                    iec.append(ecStr);
                }
            }
        }

        // Setting IEC string constructed from IC + EC
        String iecStr = iec.toString();
        if (!WhoConverter.isEmptyOrBlankOrNull(iecStr)) {
            study.setAttribute("iec", iecStr);
        }
    }

    /**
     * Write to WHO log file with timestamp.
     * 
     * @param text the log text
     */
    public void writeLog(String text) {
        try {
            if (this.logWriter != null) {
                if (this.trialID != null) {
                    this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + this.trialID + " - " + text + "\n");
                    this.logWriter.flush();
                } else {
                    System.out.println("WHO - Trial ID null (cannot write logs)");
                }
            } else {
                System.out.println("WHO - Log writer is null (cannot write logs)");
            }
        } catch(IOException e) {
            System.out.println("WHO - Couldn't write to log file");
        }
    }

    /**
     * Check if a string is null, empty, only contains whitespaces, or is equal to "NULL".
     * 
     * @return true if null or empty or only contains whitespaces or is equal to "NULL", false otherwise
     */
    public static boolean isEmptyOrBlankOrNull(String s) {
        return (s == null || s.isEmpty() || s.isBlank() || s.equalsIgnoreCase("NULL"));
    }

    /**
     * Normalise word and add trailing s to unit.
     * 
     * @param u the unit to normalise
     * @return the normalised unit
     * @see #normaliseWord()
     */
    public static String normaliseUnit(String u) {
        return WhoConverter.normaliseWord(u) + "s";
    }

    /**
     * Normalise study type by normalising word and adding a trailing "al" to the word (intervention/observation)
     * 
     * @param t the study type to normalise
     * @return the normalised type
     * @see #normaliseWord()
     */
    public static String normaliseType(String t) {
        return WhoConverter.normaliseWord(t) + "al";
    }

    /**
     * Convert phase number (1-4) to digit string. Only returns a different string if the input is in Roman numerals.
     * 
     * @param n the input digit string, possibly in roman numerals
     * @return the converted phase number
     */
    public static String convertPhaseNumber(String n) {
        return WhoConverter.PHASE_NUMBER_MAP.get(n.toLowerCase());
    }

    /**
     * Uppercase first letter and lowercase the rest.
     * 
     * @param w the word to normalise
     * @return the normalised word
     */
    public static String normaliseWord(String w) {
        if (w.length() > 0) {
            w = w.substring(0,1).toUpperCase() + w.substring(1).toLowerCase();
        }
        return w;
    }

    /**
     * Get field value from array of values using a field's position-lookup Map, value is also cleaned.
     * 
     * @param lineValues the list of all values for a line in the data file
     * @param field the name of the field to get the value of
     * @return the cleaned value of the field
     * @see #cleanValue()
     */
    public String getAndCleanValue(String[] lineValues, String field) {
        // TODO: handle errors
        return WhoConverter.cleanValue(lineValues[this.fieldsToInd.get(field)].strip());
    }

    /**
     * Get field value from array of values using a field's position-lookup Map, value is also cleaned without stripping it of leading/trailing whitespaces.
     * 
     * @param lineValues the list of all values for a line in the data file
     * @param field the name of the field to get the value of
     * @return the cleaned value of the field
     * @see #cleanValue()
     */
    public String getAndCleanValueNoStrip(String[] lineValues, String field) {
        // TODO: handle errors
        return WhoConverter.cleanValue(lineValues[this.fieldsToInd.get(field)]);
    }

    /**
     * Remove extra quotes, unescape HTML chars, and strip the string of empty spaces.
     * 
     * @param s the value to clean
     * @return the cleaned value
     * @see #unescapeHtml()
     * @see #removeQuotes()
     */
    public static String cleanValue(String s) {
        return WhoConverter.unescapeHtml(WhoConverter.removeQuotes(s)).strip();
    }

    /**
     * Remove extra quotes, and unescape HTML chars.
     * 
     * @param s the value to clean
     * @return the cleaned value
     * @see #unescapeHtml()
     * @see #removeQuotes()
     */
    public static String cleanValueNoStrip(String s) {
        return WhoConverter.unescapeHtml(WhoConverter.removeQuotes(s));
    }

    /**
     * Unescape HTML4 characters.
     * 
     * @param s the string potentially containing escaped HTML4 characters
     * @return the unescaped string
     */
    public static String unescapeHtml(String s) {
        // TODO: replace with jsoup unescaping
        // return StringEscapeUtils.unescapeHtml4(s);
        return Jsoup.parse(s).text();
    }

    /**
     * Remove leading and trailing double quotes from a string.
     * Unfortunately opencsv only transforms triple double-quoted values into single double-quotes values, 
     * so we have to remove the remaining quotes manually.
     * 
     * @param s the string to remove quotes from
     * @return the string without leading and trailing double quotes
     */
    public static String removeQuotes(String s) {
        if (s != null && s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length()-1) == '"') {
            return s.substring(1, s.length()-1);
        }
        return s;
    }

    /**
     * Test if a string is a positive whole number.
     * 
     * @param s the string to test
     * @return true if string is a positive whole number, false otherwise
     */
    public static boolean isPosWholeNumber(String s) {
        if (s.length() == 0) { return false; }
        
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                continue;
            }
            return false;
        }

        return true;
    }

    /**
     * Get a dictionary (map) of the WHO data file field names linked to their corresponding column index in the data file, using a separate headers file.
     * The headers file path is defined in the project.xml file (and set as an instance attribute of this class).
     * 
     * @return map of data file field names and their corresponding column index
     */
    public Map<String, Integer> getHeaders() throws Exception {
        if (this.headersFilePath.equals("")) {
            throw new Exception("headersFilePath property not set in mdrmine project.xml");
        }

        if (!(new File(this.headersFilePath).isFile())) {
            throw new Exception("WHO Headers file does not exist (path tested: " + this.headersFilePath + " )");
        }

        List<String> fileContent = Files.readAllLines(Paths.get(this.headersFilePath), StandardCharsets.UTF_8);
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();

        if (fileContent.size() > 0) {
            String headersLine = String.join("", fileContent).strip();
            // Deleting the invisible \FEFF unicode character at the beginning of the header file
            if (Integer.toHexString(headersLine.charAt(0) | 0x10000).substring(1).toLowerCase().equals("feff")) {
                headersLine = headersLine.substring(1);
            }
            String[] fields = headersLine.split(",");
            
            for (int ind = 0; ind < fields.length; ind++) {
                fieldsToInd.put(fields[ind], ind);
            }
        } else {
            throw new Exception("WHO Headers file is empty");
        }

        return fieldsToInd;
    }
}
