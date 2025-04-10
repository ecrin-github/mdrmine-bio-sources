package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024-2025 MDRMine
 * Modified from 2002-2019 FlyMine 
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import com.opencsv.exceptions.CsvMalformedLineException;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.WordUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;



/**
 * Class to parse values from a WHO data file and store them as MDRMine items
 * @author ECRIN
 */
public class WhoConverter extends CacheConverter
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
    private static final Pattern P_URL = Pattern.compile("\\b[^\\s]+\\.(pdf|doc|[a-zA-Z]).*\\b", Pattern.CASE_INSENSITIVE); // TODO: improve probably
    private static final Pattern P_FIX_EUCTR = Pattern.compile("(?<!:)\\/\\/", Pattern.CASE_INSENSITIVE);

    private static final String IC_PREFIX = "Inclusion criteria: ";
    private static final String EC_PREFIX = "Exclusion criteria: ";

    private static final String DATASET_TITLE = "ICTRPFullExport-1003291-20-06-2024.csv";
    private static final String DATA_SOURCE_NAME = "WHO";

    private String headersFilePath = "";
    private Map<String, Integer> fieldsToInd;
    private String registry;    // Registry name
    private boolean cache;  // Caching if new EUCTR study

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
     * Process WHO data file by iterating on each line of the data file.
     * Method called by InterMine.
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        this.startLogging("who");

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
                this.parseAndStoreValues(nextLine);
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

        this.storeAllItems();

        this.stopLogging();
        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    /**
     * Parse and store values as MDRMine items and attributes, from a list of values of a line of the data file.
     * 
     * @param lineValues the list of raw values of a line in the data file
     */
    public void parseAndStoreValues(String[] lineValues) throws Exception {
        Item study = null;
        this.registry = "";
        this.cache = false;
        this.newerLastUpdate = false;
        this.existingStudy = null;
        this.currentCountry = null;
        
        String trialID = this.getAndCleanValue(lineValues, "TrialID");
        String cleanedID = null;

        // Handling EUCTR trials
        if (trialID.startsWith("EUCTR")) {
            this.registry = ConverterCVT.R_EUCTR;
            // ID without country code suffix
            cleanedID = trialID.substring(0, 19);
            String countryCode = trialID.substring(20, 22);
            
            if (this.studies.containsKey(cleanedID)) {   // Adding country-specific info to existing trial
                this.existingStudy = this.studies.get(cleanedID);
                study = this.existingStudy;
                this.writeLog("Add country info, trial ID: " + cleanedID);
            } else {
                this.cache = true;
            }

            // Using ID without country code suffix
            this.currentTrialID = cleanedID;

            // Getting country from ID country code
            if (!ConverterUtils.isNullOrEmptyOrBlank(countryCode)) {
                this.currentCountry = this.getCountryFromField("isoAlpha2", countryCode);
                if (this.currentCountry == null) {
                    this.writeLog("Couldn't find country from country code: " + countryCode);
                }
            }
        } else {
            this.currentTrialID = trialID;
        }

        if (!this.existingStudy()) {    // Regular parsing
            study = createItem("Study");
        }

        // TODO: study end date? -> results posted date?
        // Used for registry entry DO
        String lastUpdateStr = this.getAndCleanValue(lineValues, "last_update");
        LocalDate lastUpdate = this.parseDate(lastUpdateStr, ConverterUtils.P_DATE_D_MWORD_Y_SPACES);
        // TODO: skip creating study if ID is missing?

        /* ID and ID URL */
        String url = this.getAndCleanValue(lineValues, "url");

        // TODO EUCTR: add ID with suffix as well?
        if (!this.existingStudy()) {
            // TODO: fix duplicate studyIdentifier error
            this.createAndStoreStudyIdentifier(study, this.currentTrialID, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, url);
        }

        /* Secondary IDs */
        // TODO EUCTR: check for additional IDs with existing study
        // Secondary IDs can also include project IDs (e.g. P30CA015704) and therefore appear multiple times, not parsing them for now
        String secondaryIDs = this.getAndCleanValue(lineValues, "SecondaryIDs");
        // this.parseSecondaryIDs(study, secondaryIDs);

        // TODO EUCTR: check for additional titles with existing study
        /* Public title */
        String publicTitle = this.getAndCleanValue(lineValues, "public_title");
        this.parseTitle(study, publicTitle, ConverterCVT.TITLE_TYPE_PUBLIC);

        /* Scientific title */
        String scientificTitle = this.getAndCleanValue(lineValues, "Scientific_title");
        this.parseTitle(study, scientificTitle, ConverterCVT.TITLE_TYPE_SCIENTIFIC);

        // TODO: not working as intended
        Item studySource = createItem("StudySource");
        studySource.setAttributeIfNotNull("sourceName", "WHO");
        studySource.setReference("study", study);
        store(studySource);
        study.addToCollection("studySources", studySource);

        // TODO EUCTR: check for additional contacts with existing study
        /* Study people (public and scientific contacts) */
        this.parseContact(study, lineValues, "public");
        this.parseContact(study, lineValues, "scientific");

        /* Study type */
        this.parseStudyType(study, this.getAndCleanValue(lineValues, "study_type"));

        /* Study feature: phase */
        String phase = this.getAndCleanValue(lineValues, "phase");
        this.parsePhase(study, phase);

        /* Study features */
        String studyDesign = this.getAndCleanValue(lineValues, "study_design");
        this.parseStudyDesign(study, studyDesign);

        /* Brief description 1/3 (study design) */
        if (!this.existingStudy()) {
            // Note: using the fields currently used by the MDR to construct the briefDescription value
            // TODO: change/improve this field?
            ConverterUtils.addToBriefDescription(study, studyDesign);
        }

        /* Registry entry DO */
        // "Date on which this record was first entered in the EudraCT database:" in EUCTR
        String registrationDateStr = this.getAndCleanValue(lineValues, "Date_registration");
        LocalDate registrationDate = this.parseDate(registrationDateStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);

        /* Date enrolment (start date) */
        // "Date of Competent Authority Decision" in EUCTR
        String dateEnrolmentStr = this.getAndCleanValue(lineValues, "Date_enrollement");
        study.setAttributeIfNotNull("testField3", dateEnrolmentStr);
        LocalDate dateEnrolment = this.parseDate(dateEnrolmentStr, null);
        this.setStudyStartDate(study, dateEnrolment);

        // Results date posted also used later
        String resultsDatePostedStr = this.getAndCleanValue(lineValues, "results_date_posted");
        LocalDate resultsDatePosted = this.parseDate(resultsDatePostedStr, null);
        String publicationYear = resultsDatePosted != null ? String.valueOf(resultsDatePosted.getYear()) : "";
        study.setAttributeIfNotNull("testField1", lastUpdate != null ? lastUpdate.toString() : null);
        study.setAttributeIfNotNull("testField2", registrationDate != null ? registrationDate.toString() : null);

        // TODO EUCTR: replace updated date + registration date + publication year if existing study + start date more recent
        // TODO: publication year wrong
        this.createAndStoreRegistryEntryDO(study, url, registrationDate, publicationYear, lastUpdate);

        /* Study planned enrolment */
        // In EUCTR: for the whole clinical trial
        // TODO: handle "verbose" values, example study: SLCTR/2017/032
        String targetSize = this.getAndCleanValue(lineValues, "Target_size");
        if (ConverterUtils.isPosWholeNumber(targetSize) && !(Long.valueOf(targetSize) > Integer.MAX_VALUE)) {
            if (!this.existingStudy() || this.newerLastUpdate) {    // Updating planned enrolment for more recent last update date
                study.setAttributeIfNotNull("plannedEnrolment", targetSize);
            }
        }
        
        /* Study actual enrolment */
        // In EUCTR: for the whole clinical trial
        String resultsActualEnrolment = this.getAndCleanValue(lineValues, "results_actual_enrollment");
        if (ConverterUtils.isPosWholeNumber(resultsActualEnrolment) && !(Long.valueOf(resultsActualEnrolment) > Integer.MAX_VALUE)) {
            if (this.newerLastUpdate) { // Updating actual enrolment for more recent last update date
                study.setAttributeIfNotNull("actualEnrolment", resultsActualEnrolment);
            }
        }

        /* Study status */
        // TODO: normalise values
        // TODO EUCTR: logically replace values (e.g. recruiting instead of recruitment ended) 
        String studyStatus = this.getAndCleanValue(lineValues, "Recruitment_status");
        if (!this.existingStudy()) {
            study.setAttributeIfNotNull("status", studyStatus);
        }

        /* Study people: sponsors */
        String primarySponsor = this.getAndCleanValue(lineValues, "Primary_sponsor");
        String secondarySponsors = this.getAndCleanValue(lineValues, "Secondary_sponsors");
        String sourceSupport = this.getAndCleanValue(lineValues, "Source_Support");

        // TODO EUCTR: check for new sponsors for existing study
        if (!this.existingStudy()) {
            /* Primary and secondary sponsors, scientific support organisation (source support)*/
            this.createAndStoreStudyOrg(study, primarySponsor, ConverterCVT.CONTRIBUTOR_TYPE_SPONSOR);
            // TODO: there may be multiple sponsors in this field
            this.createAndStoreStudyOrg(study, secondarySponsors, ConverterCVT.CONTRIBUTOR_TYPE_SPONSOR);
            // Checking if the source support string is different than the primary and secondary sponsors + is not "please refer to primary and secondary sponsors"
            if (!sourceSupport.toLowerCase().contains("please") && !sourceSupport.equalsIgnoreCase(primarySponsor) && !sourceSupport.equalsIgnoreCase(secondarySponsors)) {
                this.createAndStoreStudyOrg(study, sourceSupport, ConverterCVT.CONTRIBUTOR_TYPE_STUDY_FUNDER);
            }
        }

        /* Ethics decision date, can be a semi colon list  */
        // TODO
        String ethicsApprovalDateStr = this.getAndCleanValue(lineValues, "Ethics_Approval_Date");
        study.setAttributeIfNotNull("testField4", ethicsApprovalDateStr);

        /* Study countries */
        String countries = this.getAndCleanValue(lineValues, "Countries");
        this.parseCountries(study, countries, targetSize, dateEnrolment, ethicsApprovalDateStr);

        /* Study conditions */
        String conditions = this.getAndCleanValue(lineValues, "Conditions");
        this.parseConditions(study, conditions);

        /* Interventions */
        // TODO: use for study topics
        String interventions = this.getAndCleanValue(lineValues, "Interventions");
        study.setAttributeIfNotNull("interventions", interventions);

        /* Brief description 2/3 (interventions) */
        if (!this.existingStudy()) {
            ConverterUtils.addToBriefDescription(study, interventions);
        }

        /* Min age */
        String ageMin = this.getAndCleanValue(lineValues, "Agemin");
        this.parseAgeField(study, ageMin, "minAge", "minAgeUnit");
        /* Max age */
        String ageMax = this.getAndCleanValue(lineValues, "Agemax");
        this.parseAgeField(study, ageMax, "maxAge", "maxAgeUnit");

        /* Gender */
        String gender = this.getAndCleanValue(lineValues, "Gender");
        this.parseGender(study, gender);

        /* IEC */
        String ic = this.getAndCleanValue(lineValues, "Inclusion_Criteria");
        String ec = this.getAndCleanValue(lineValues, "Exclusion_Criteria");
        this.parseIEC(study, ic, ec);

        /* Primary outcome */
        // TODO: handle main empty values
        String primaryOutcome = this.getAndCleanValue(lineValues, "Primary_Outcome");
        study.setAttributeIfNotNull("primaryOutcome", primaryOutcome);

        /* Brief description 3/3 (primary outcome) */
        if (!this.existingStudy()) {
            ConverterUtils.addToBriefDescription(study, primaryOutcome);
        }

        /* Secondary outcomes */
        // TODO: handle main empty values + N/A
        String secondaryOutcomes = this.getAndCleanValue(lineValues, "Secondary_Outcomes");
        // Additional info with results_adverse_events field
        String resultsAdverseEvents = this.getAndCleanValue(lineValues, "results_adverse_events");
        // Using resultsDatePosted field to ignore some placeholder links in resultsAdverseEvents
        this.parseSecondaryOutcomes(study, secondaryOutcomes, resultsAdverseEvents, resultsDatePosted);

        // In MDR model but unused; Unclear what the various values mean - certain bridging flag/childs 
        // values with parent or child bridged type seem to refer to the same study and not additional/children studies
        // TODO: unused?
        String bridgingFlag = this.getAndCleanValue(lineValues, "Bridging_flag");
        String bridgedType = this.getAndCleanValue(lineValues, "Bridged_type");
        String childs = this.getAndCleanValue(lineValues, "Childs");
        
        // TODO: unused? seems to be somewhat overlapping with study status, 
        // most of the time value is anticipated when status is pending and actual when status is recruiting
        // In MDR WHO model but unused
        String typeEnrolment = this.getAndCleanValue(lineValues, "type_enrolment");
        
        // Note: retrospective studies are, at the same proportion as non-retrospective studies, interventional (= the majority) -> seems wrong?
        String retrospectiveFlag = this.getAndCleanValue(lineValues, "Retrospective_flag");
        this.parseRetrospectiveFlag(study, retrospectiveFlag);
        
        /* Results summary DO */
        String resultsUrlLink = this.getAndCleanValue(lineValues, "results_url_link");
        // TODO: check if actual results summary? may not be for EUCTR trials at least
        String resultsSummary = this.getAndCleanValue(lineValues, "results_summary");
        // resultsDatePosted used earlier
        String resultsDateFirstPublication = this.getAndCleanValue(lineValues, "results_date_first_publication");
        // "Date of the global end of the trial" in EUCTR
        // TODO: update dates if existing study and later dates
        String resultsDateCompletedStr = this.getAndCleanValue(lineValues, "results_date_completed");
        LocalDate resultsDateCompleted = this.parseDate(resultsDateCompletedStr, null);
        this.createAndStoreResultsSummaryDO(study, resultsUrlLink, resultsSummary, resultsDatePosted, resultsDateCompleted);

        /* Results */

        // Not in MDR, baseline chars = Factors that describe study participants at the beginning of the study
        // Could fall under ObjectDescription, creating a new DO? -> what would be object type? or perhaps in study's briefDescription
        String resultsBaselineChar = this.getAndCleanValue(lineValues, "results_baseline_char");
        // Not in MDR, indicates participation dropout/follow-up (flow), unused or to add to briefdescription
        String resultsParticipantFlow = this.getAndCleanValue(lineValues, "results_participant_flow");
        // Not in MDR, outcome measures text, unused or add somehow to both primary and secondary outcomes
        String resultsOutcomeMeasures = this.getAndCleanValue(lineValues, "results_outcome_measures");

        /* Protocol DO */
        String resultsUrlProtocol = this.getAndCleanValue(lineValues, "results_url_protocol");
        // TODO: publication year irrelevant?
        this.createAndStoreProtocolDO(study, resultsUrlProtocol, publicationYear);

        // Indicates if there is a plan to share IPD
        String resultsIPDPlan = this.getAndCleanValue(lineValues, "results_IPD_plan");
        // Reason to not share if no plan to share, otherwise access details/sharing plan details
        String resultsIPDDescription = this.getAndCleanValue(lineValues, "results_IPD_description");
        this.parseDataSharingStatement(study, resultsIPDPlan, resultsIPDDescription);

        // In MDR WHO model but unused, 9% of yes, 91% of empty values
        String resultsYesNo = this.getAndCleanValue(lineValues, "results_yes_no");

        /* Ethics */
        // Not in MDR, ethics approval status, can have a list of values
        String ethicsStatus = this.getAndCleanValue(lineValues, "Ethics_Status");
        // Ethics_Approval_Date used earlier
        // Not in MDR, email, can be a list of emails
        String ethicsContactName = this.getAndCleanValue(lineValues, "Ethics_Contact_Name");
        // Not in MDR, name of ethics committee (not exactly address)
        String ethicsContactAddress = this.getAndCleanValue(lineValues, "Ethics_Contact_Address");
        // Not in MDR, name of ethics committee (not exactly address)
        String ethicsContactEmail = this.getAndCleanValue(lineValues, "Ethics_Contact_Email");

        if (!this.existingStudy()) {
            if (cache) {
                this.studies.put(this.currentTrialID, study);
            } else {
                store(study);
            }
        }

        this.currentTrialID = null;
    }

    /**
     * Parse secondary IDs input to create StudyIdentifier items.
     * 
     * @param study the study item to link to study identifiers
     * @param secIDsStr the input secondary IDs string
     */
    public void parseSecondaryIDs(Item study, String secIDsStr) throws Exception {
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(secIDsStr)) {
            HashSet<String> seenIds = new HashSet<String>();

            String[] ids = secIDsStr.split(";");
            for (String id: ids) {
                if (!ConverterUtils.isNullOrEmptyOrBlank(id) && !seenIds.contains(id)) {
                    this.createAndStoreClassItem(study, "StudyIdentifier", 
                        new String[][]{{"identifierValue", id}});
                    seenIds.add(id);
                }
                // TODO: identifier type
                // TODO: identifier link
            }
        }
    }

    /**
     * TODO
     */
    public void parseTitle(Item study, String title, String titleType) throws Exception {
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(title) && !title.equals("-") && !title.equals("_") && !title.equals(".")) {
            this.createAndStoreClassItem(study, "Title",
                new String[][]{{"text", title}, {"type", titleType}});
            
            if (!study.hasAttribute("displayTitle")) {
                study.setAttributeIfNotNull("displayTitle", title);
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
        if (!this.existingStudy()) {
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
            if (!ConverterUtils.isNullOrEmptyOrBlank(firstNamesString)) {
                firstNames = firstNamesString.split(";");
            }

            String lastNamesString = this.getAndCleanValueNoStrip(lineValues, (fieldPrefix + "Contact_Lastname"));
            if (!ConverterUtils.isNullOrEmptyOrBlank(lastNamesString)) {
                lastNames = lastNamesString.split(";");
            }

            String affiliationsString = this.getAndCleanValueNoStrip(lineValues, (fieldPrefix + "Contact_Affiliation"));
            if (!ConverterUtils.isNullOrEmptyOrBlank(affiliationsString)) {
                affiliations = affiliationsString.split(";");
            }

            /* Address and phone strings present in WHO are unused here since they don't appear in our model */
            // Using the field with the most semi-colon-separated elements for iterating
            int maxLen = Math.max(Math.max(firstNames.length, lastNames.length), affiliations.length);

            String firstName, lastName, affiliation;

            if (maxLen > 0) {
                for (int i = 0; i < maxLen; i++) {
                    firstName = "";
                    lastName = "";
                    affiliation = "";

                    // TODO: possible that the string ends with both semi-colons?
                    
                    // First name
                    if (!ConverterUtils.isNullOrEmptyOrBlank(firstNamesString)) {
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
                    if (!ConverterUtils.isNullOrEmptyOrBlank(lastNamesString)) {
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
                    if (!ConverterUtils.isNullOrEmptyOrBlank(affiliationsString)) {
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
                    this.createAndStoreContact(study, firstName, lastName, affiliation, contactType);

                    // TODO: handle affiliation differently? (same for multiple people) NCT04163835
                    // TODO: how to avoid duplicates that are not really duplicates? NCT04163835
                    // TODO: also set organisation field?
                    // Check MDR code for this: https://github.com/scanhamman/MDR_Harvester/blob/af313e05f60012df56c8a6dd3cbb73a9fe1cd906/GeneralHelpers/StringFunctions.cs#L947
                }
            }
        }
    }

    /**
     * TODO
     * 
     * @param firstName the first name value
     * @param lastName the last name value
     * @param affiliation the affiliation value
     * @param contactType public or scientific contact
     */
    public void createAndStoreContact(Item study, String firstName, String lastName, String affiliation, String contactType) throws Exception {
        // TODO: attempt at separating first name/last name if one of firstName/lastName is empty?
        // Check MDR code for this: https://github.com/scanhamman/MDR_Harvester/blob/master/GeneralHelpers/StringFunctions.cs#L714

        String givenName = null;
        String familyName = null;
        String fullName = null;
        String contribType = null;
        
        if (!ConverterUtils.isNullOrEmptyOrBlank(firstName) && !ConverterUtils.isNullOrEmptyOrBlank(lastName)) {
            givenName = firstName;
            familyName = lastName;
            fullName = firstName + " " + lastName;
        } else if (!ConverterUtils.isNullOrEmptyOrBlank(firstName)) {
            fullName = firstName;
        } else {
            fullName = lastName;
        }

        if (contactType.equalsIgnoreCase("public")) {
            contribType = ConverterCVT.CONTRIBUTOR_TYPE_PUBLIC_CONTACT;
        } else {
            // Exception already thrown earlier so value can't be anything other than "scientific"
            contribType = ConverterCVT.CONTRIBUTOR_TYPE_SCIENTIFIC_CONTACT;
        }

        // TODO: fix affiliation
        this.createAndStoreClassItem(study, "Person",
            new String[][]{{"givenName", givenName}, {"familyName", familyName},
                            {"fullName", fullName}, {"affiliation", affiliation}, {"contribType", contribType}});
    }

    /**
     * Parse min/max age value to set age value and unit fields.
     * 
     * @param study the study item containing the attributes to set
     * @param ageStr the age string
     * @param ageAttr the age attribute name to set (either minAge or maxAge)
     * @param unitAttr the unit attribute name to set (either minAgeUnit or maxAgeUnit)
     */
    public void parseAgeField(Item study, String ageStr, String ageAttr, String unitAttr) {
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(ageStr)) {
            // Check for N/A or no limit
            Matcher mAgeNotApplicable = P_AGE_NOT_APPLICABLE.matcher(ageStr);
            if (mAgeNotApplicable.matches()) {
                study.setAttributeIfNotNull(ageAttr, ConverterCVT.NOT_APPLICABLE);
            } else {    // Not stated
                Matcher mAgeNotStated = P_UNKNOWN.matcher(ageStr);
                if (mAgeNotStated.matches()) {
                    study.setAttributeIfNotNull(ageAttr, ConverterCVT.NOT_STATED);
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

                                    study.setAttributeIfNotNull(ageAttr, String.valueOf(ageNumber));
                                } else {    // Case where min age value is float
                                    study.setAttributeIfNotNull(ageAttr, g2);
                                }

                                // TODO: how to check unit against data?
                                if (!(g3 == null || g3.equalsIgnoreCase("age"))) {
                                    study.setAttributeIfNotNull(unitAttr, ConverterUtils.normaliseUnit(g3));
                                } else {    // If no unit (or unit is "age"), we assume it's years
                                    study.setAttributeIfNotNull(unitAttr, "Years");
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

        if ((!this.existingStudy() || !study.hasAttribute("type")) && !ConverterUtils.isNullOrEmptyOrBlank(studyTypeStr)) {
            Matcher mTypeInterventional = P_TYPE_INTERVENTIONAL.matcher(studyTypeStr);
            if (mTypeInterventional.matches()) {    // Interventional
                study.setAttributeIfNotNull("type", ConverterCVT.TYPE_INTERVENTIONAL);
            } else {    // Observational
                Matcher mTypeObservational = P_TYPE_OBSERVATIONAL.matcher(studyTypeStr);
                if (mTypeObservational.matches()) {
                    study.setAttributeIfNotNull("type", ConverterCVT.TYPE_OBSERVATIONAL);
                } else {    // Other
                    Matcher mTypeOther = P_TYPE_OTHER.matcher(studyTypeStr);
                    if (mTypeOther.matches()) {
                        study.setAttributeIfNotNull("type", ConverterCVT.TYPE_OTHER);
                    } else {    // Basic science
                        Matcher mTypeBasicScience = P_TYPE_BASIC_SCIENCE.matcher(studyTypeStr);
                        if (mTypeBasicScience.matches()) {
                            study.setAttributeIfNotNull("type", ConverterCVT.TYPE_BASIC_SCIENCE);
                        } else {    // N/A
                            Matcher mTypeNA = P_NOT_APPLICABLE.matcher(studyTypeStr);
                            if (mTypeNA.matches()) {
                                study.setAttributeIfNotNull("type", ConverterCVT.NOT_APPLICABLE);
                            } else {    // Expanded access
                                Matcher mTypeExpandedAcess = P_TYPE_EXPANDED_ACCESS.matcher(studyTypeStr);
                                if (mTypeExpandedAcess.matches()) {
                                    study.setAttributeIfNotNull("type", ConverterCVT.TYPE_EXPANDED_ACCESS);
                                } else {    // Unknown
                                    study.setAttributeIfNotNull("type", ConverterCVT.UNKNOWN);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Parse phase input to create a StudyFeature item.
     * 
     * @param study the study item to link to study features
     * @param phaseStr the input phase string
     */
    public void parsePhase(Item study, String phaseStr) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(phaseStr) && (!this.existingStudy() || !this.hasPhaseFeature(study))) {
            String featureValue = null;

            Matcher mPhaseNumber = P_PHASE_NUMBER.matcher(phaseStr);
            if (mPhaseNumber.matches()) {   // "Numbers" match
                String phase = mPhaseNumber.group(1);
                String nb1 = mPhaseNumber.group(2);
                String nb2 = mPhaseNumber.group(3);
                if (nb1 != null) {
                    if ((phase != null && phase.toLowerCase().contains("early")) || nb1.equals("0")) {  // Early phase 1
                        featureValue = ConverterCVT.FEATURE_V_EARLY_PHASE_1;
                        if (nb2 != null) {
                            this.writeLog("Anomaly: second number matched for early phase string, phase: " 
                                            + phase + "; nb1: " + nb1 + "; nb2: " + nb2 + ", full string: " + phaseStr);
                        }
                    } else if (nb2 != null && !nb1.equalsIgnoreCase(nb2)) {   // Two phases
                        featureValue = "Phase " + ConverterUtils.constructMultiplePhasesString(nb1, nb2);
                    } else {    // One phase
                        featureValue = "Phase " + ConverterUtils.convertPhaseNumber(nb1);
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
                                featureValue = "Phase " + String.valueOf(phasesRes.get(0));
                            } else if (phasesRes.size() == 2) { // Two phases
                                featureValue = "Phase " + String.valueOf(phasesRes.get(0)) + "/" + String.valueOf(phasesRes.get(1));
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
                        featureValue = ConverterCVT.NOT_APPLICABLE;
                    } else {    // Using raw value
                        featureValue = phaseStr;
                    }
                }
            }

            if (!ConverterUtils.isNullOrEmptyOrBlank(featureValue)) {
                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_PHASE}, {"featureValue", featureValue}});
            }
        }
    }

    /**
     * TODO
     */
    public boolean hasPhaseFeature(Item study) {
        return this.getItemFromItemMap(study, this.studyFeatures, "featureType", ConverterCVT.FEATURE_T_PHASE) != null;
    }

    /**
     * Parse study features input to create StudyFeature items.
     * 
     * @param study the study item to link to study features
     * @param featuresStr the input study features string
     */
    public void parseStudyDesign(Item study, String featuresStr) throws Exception {
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(featuresStr)) {
            // TODO: improve parsing of study features, currently it works only for most interventional studies
            Matcher mFeatureInterventional = P_FEATURE_INTERVENTIONAL.matcher(featuresStr);
            if (mFeatureInterventional.matches()) { // Interventional features
                String allocation = mFeatureInterventional.group(2);
                String model = mFeatureInterventional.group(5);
                String purpose = mFeatureInterventional.group(7);
                String masking = mFeatureInterventional.group(9);

                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_ALLOCATION}, {"featureValue", allocation}});
                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_INTERVENTION_MODEL}, {"featureValue", model}});
                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_PRIMARY_PURPOSE}, {"featureValue", purpose}});
                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_MASKING}, {"featureValue", masking}});
            } else {    // Using raw value
                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureValue", featuresStr}});
            }
        }
    }

    /**
     * TODO
     */
    public void parseSecondaryOutcomes(Item study, String secondaryOutcomes, String resultsAdverseEvents, LocalDate resultsDatePosted) {
        if (!this.existingStudy()) {
            StringBuilder constructedSecondaryOutcomes = new StringBuilder();
            if (!ConverterUtils.isNullOrEmptyOrBlank(secondaryOutcomes)) {
                constructedSecondaryOutcomes.append(secondaryOutcomes);
            }
            // TODO: check that resultsDatePosted date is valid
            // Filtering out placeholder links (drks.de)
            if (!ConverterUtils.isNullOrEmptyOrBlank(resultsAdverseEvents) 
                && !(resultsAdverseEvents.contains("drks.de") && resultsDatePosted != null)) {
                if (secondaryOutcomes.length() > 0) {
                    if (!secondaryOutcomes.endsWith(".")) {
                        constructedSecondaryOutcomes.append(".");
                    }
                    constructedSecondaryOutcomes.append(" ");
                }
                constructedSecondaryOutcomes.append("Adverse events: ");
                constructedSecondaryOutcomes.append(resultsAdverseEvents);
            }
            
            if (!ConverterUtils.isNullOrEmptyOrBlank(constructedSecondaryOutcomes.toString())) {
                study.setAttributeIfNotNull("secondaryOutcomes", constructedSecondaryOutcomes.toString());
            }
        }
    }

    /**
     * TODO
     * TODO should return created studyPeople
     */
    public void createAndStoreStudyOrg(Item study, String studyOrgStr, String contribType) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(studyOrgStr)) {
            // TODO: setting studyOrganisation object for now, need logic to distinguish people from orgs

            // TODO: createAndStoreClassItem needs to deal with many2many as well

            Matcher mNA = P_NOT_APPLICABLE.matcher(studyOrgStr);
            if (mNA.matches()) {    // N/A
                this.createAndStoreClassItem(study, "Organisation",
                    new String[][]{{"contribType", contribType}, 
                                    {"name", ConverterCVT.NOT_APPLICABLE}});
            } else {
                Matcher mNone = P_NONE.matcher(studyOrgStr);
                if (mNone.matches()) {   // No sponsor
                    if (contribType.equals(ConverterCVT.CONTRIBUTOR_TYPE_SPONSOR)) {
                        this.createAndStoreClassItem(study, "Organisation",
                            new String[][]{{"contribType", contribType}, 
                                            {"name", ConverterCVT.SPONSOR_NONE}});
                    }
                } else {    // Using raw value
                    this.createAndStoreClassItem(study, "Organisation",
                        new String[][]{{"contribType", contribType}, 
                                        {"name", studyOrgStr}});
                }
            }
        }
    }

    /**
     * TODO
     * @param cadDate date of competent authority decision
     * @param ecdDateStr raw date(s) string of ethics committee decision
     */
    public void parseCountries(Item study, String countriesStr, String plannedEnrolment, LocalDate cadDate, String ecdDatesStr) throws Exception {
        // TODO: only restrict date fields + enrolment to EUCTR?
        // TODO: try to normalise values
        // TODO: parse few values where multiple-country delimiter is comma instead of semi-colon
        String status = ConverterUtils.getValueOfItemAttribute(study, "status");

        if (!ConverterUtils.isPosWholeNumber(plannedEnrolment) || Long.valueOf(plannedEnrolment) > Integer.MAX_VALUE) {
            plannedEnrolment = null;
        }

        if (!this.existingStudy()) {
            // TODO: parse edcDates
            // TODO: don't add duplicates (EUCTR2008-007326-19)
            if (!ConverterUtils.isNullOrEmptyOrBlank(countriesStr)) {
                if (countriesStr.contains(";")) {
                    boolean foundCurrentCountry = false;

                    String[] countriesList = countriesStr.split(";");
                    for (String countryStr: countriesList) {
                        if (!ConverterUtils.isNullOrEmptyOrBlank(countryStr)) {
                            if (this.currentCountry != null && countryStr.equalsIgnoreCase(this.currentCountry.getName())) {    // Setting more info for current country
                                this.createAndStoreStudyCountry(study, countryStr, status, plannedEnrolment, cadDate, null);
                                foundCurrentCountry = true;
                            } else {    // Regular parsing
                                this.createAndStoreStudyCountry(study, countryStr, null, null, null, null);
                            }
                        }
                    }

                    // Creating country if currentCountry is set but couldn't match it with a country in the country list
                    if (this.currentCountry != null && !foundCurrentCountry) {
                        this.createAndStoreStudyCountry(study, this.currentCountry.getName(), status, plannedEnrolment, cadDate, null);
                    }
                } else {
                    this.createAndStoreStudyCountry(study, countriesStr, status, plannedEnrolment, cadDate, null);
                }
            }
        } else if (this.currentCountry != null) {    // Updating an existing study
            // LocalDate ecdDate = this.parseDate(ecdDatesStr, ConverterUtils.P_DATE_MWORD_D_Y_HOUR);

            Item studyCountry = this.getItemFromItemMap(study, this.studyCountries, "countryName", this.currentCountry.getName());
            if (studyCountry != null) {
                studyCountry.setAttributeIfNotNull("status", status);
                studyCountry.setAttributeIfNotNull("plannedEnrolment", plannedEnrolment);
                
                // "dateEnrolment" is "Date of Competent Authority Decision" in EUCTR
                if (cadDate != null) {
                    studyCountry.setAttributeIfNotNull("compAuthorityDecisionDate", cadDate.toString());
                }
                
                // "Date of Ethics Committee Opinion" in EUCTR
                // if (ecdDate != null) {
                //     studyCountry.setAttributeIfNotNull("ethicsCommitteeDecisionDate", ecdDate.toString());
                // }
            } else {
                this.writeLog("Couldn't find StudyCountry with this current country (creating it): \"" + this.currentCountry);
                this.createAndStoreStudyCountry(study, this.currentCountry.getName(), status, plannedEnrolment, cadDate, null);
            }
        }
    }

    /**
     * TODO
     */
    public void parseConditions(Item study, String conditionsStr) throws Exception {
        if (!this.existingStudy()) {
            // TODO: match values with CT codes/ICD Codes
            if (!ConverterUtils.isNullOrEmptyOrBlank(conditionsStr)) {
                if (conditionsStr.contains(";")) {
                    String[] conditionsList = conditionsStr.split(";");
                    for (String conditionStr: conditionsList) {
                        if (!ConverterUtils.isNullOrEmptyOrBlank(conditionStr)) {
                            this.createAndStoreClassItem(study, "StudyCondition", 
                                new String[][]{{"originalValue", WordUtils.capitalizeFully(conditionStr, ' ', '-')}});
                        }
                    }
                } else {
                    this.createAndStoreClassItem(study, "StudyCondition", 
                        new String[][]{{"originalValue", WordUtils.capitalizeFully(conditionsStr, ' ', '-')}});
                }
            }
        }
    }

    /**
     * TODO
     */
    public void parseRetrospectiveFlag(Item study, String retrospectiveFlag) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(retrospectiveFlag) && !this.existingStudy()) {
            if (retrospectiveFlag.equalsIgnoreCase("1")) {
                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_TIME_PERSPECTIVE},
                                    {"featureValue", ConverterCVT.FEATURE_V_RETROSPECTIVE}});
            } else {
                this.writeLog("Retrospective flag: value is not empty but not equal to 1: " + retrospectiveFlag);
            }
        }
    }

    /**
     * TODO
     * TODO: should return created DO
     */
    public void createAndStoreResultsSummaryDO(Item study, String resultsUrlLink, String resultsSummary, 
                                                  LocalDate resultsDatePosted, LocalDate resultsDateCompleted) throws Exception {
        // TODO: filter out non-urls
        // Note: current MDR avoids duplicate with registry entry url, but maybe it makes sense to have duplicate
        // Filtering out drks.de URL with no date posted (they are placeholders)
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(resultsUrlLink) 
            && !resultsUrlLink.contains("drks.de") && resultsDatePosted != null) {
            // Display title
            String studyDisplayTitle = ConverterUtils.getValueOfItemAttribute(study, "displayTitle");
            String doDisplayTitle;
            if (!ConverterUtils.isNullOrEmptyOrBlank(studyDisplayTitle)) {
                doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TITLE_RESULTS_SUMMARY;
            } else {
                doDisplayTitle = ConverterCVT.O_TITLE_RESULTS_SUMMARY;
            }

            /* Results summary DO */
            Item resultsSummaryDO = this.createAndStoreClassItem(study, "DataObject", 
                                        new String[][]{{"title", ConverterCVT.O_TITLE_RESULTS_SUMMARY},
                                                        {"displayTitle", doDisplayTitle}, {"objectClass", ConverterCVT.O_CLASS_TEXT}, 
                                                        {"objectType", ConverterCVT.O_TYPE_TRIAL_REGISTRY_RESULTS_SUMMARY}});
            /* Instance with results URL */
            // TODO: system? (=source)
            this.createAndStoreClassItem(resultsSummaryDO, "ObjectInstance", 
                                        new String[][]{{"url", resultsUrlLink}, {"resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT}});
            
            // Results completed date
            this.createAndStoreObjectDate(resultsSummaryDO, resultsDateCompleted, ConverterCVT.DATE_TYPE_CREATED);

            // Results posted date
            if (resultsDatePosted != null) {
                this.createAndStoreObjectDate(resultsSummaryDO, resultsDatePosted, ConverterCVT.DATE_TYPE_AVAILABLE);
                // Publication year
                String publicationYear = String.valueOf(resultsDatePosted.getYear());
                if (!ConverterUtils.isNullOrEmptyOrBlank(publicationYear)) {
                    resultsSummaryDO.setAttributeIfNotNull("publicationYear", publicationYear);
                }
            }
        }
    }

    /**
     * TODO
     */
    public void createAndStoreProtocolDO(Item study, String resultsUrlProtocol, String publicationYear) throws Exception {
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(resultsUrlProtocol)) {
            Matcher mUrl = P_URL.matcher(resultsUrlProtocol);
            if (mUrl.find()) {
                // Display title
                String doDisplayTitle;
                String studyDisplayTitle = ConverterUtils.getValueOfItemAttribute(study, "displayTitle");
                if (!ConverterUtils.isNullOrEmptyOrBlank(studyDisplayTitle)) {
                    doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TYPE_STUDY_PROTOCOL;
                } else {
                    doDisplayTitle = ConverterCVT.O_TYPE_STUDY_PROTOCOL;
                }

                /* Protocol DO */
                // Object type: in practice, most of the time it's a link to the study page (e.g. CTIS) where there might be the study protocol
                // TODO: follow MDR logic then? ("CSR summary" type by default and protocol if prot in url name)

                // Access type: in practice it might not be the correct access type
                // Note: only specifying public, not using the various public types MDR has, maybe to change
                Item protocolDO = this.createAndStoreClassItem(study, "DataObject", 
                                        new String[][]{{"title", ConverterCVT.O_TYPE_STUDY_PROTOCOL}, {"displayTitle", doDisplayTitle}, 
                                                        {"objectClass", ConverterCVT.O_CLASS_TEXT}, {"objectType", ConverterCVT.O_TYPE_STUDY_PROTOCOL}, 
                                                        {"publicationYear", publicationYear}, {"accessType", ConverterCVT.O_ACCESS_TYPE_PUBLIC}});

                /* Protocol DO instance with URL */
                // TODO: Fix euctr links
                String protocolURL = P_FIX_EUCTR.matcher(mUrl.group(0)).replaceAll("/");
                
                String resourceType;
                // File extension (if any) to help determine resource type
                if (mUrl.group(1).equalsIgnoreCase("pdf")) {
                    resourceType = ConverterCVT.O_RESOURCE_TYPE_PDF;
                } else if (mUrl.group(1).equalsIgnoreCase("doc")) {
                    resourceType = ConverterCVT.O_RESOURCE_TYPE_WORD_DOC;
                } else {
                    resourceType = ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT;
                }
                
                this.createAndStoreClassItem(protocolDO, "ObjectInstance", 
                    new String[][]{{"url", protocolURL}, {"resourceType", resourceType}});
            }
        }
    }

    /**
     * TODO
     */
    public void createAndStoreRegistryEntryDO(Item study, String entryUrl, LocalDate registrationDate, String publicationYear, LocalDate lastUpdate) throws Exception {
        if (!this.existingStudy()) {
            // Display title
            String studyDisplayTitle = ConverterUtils.getValueOfItemAttribute(study, "displayTitle");
            String doDisplayTitle;
            if (!ConverterUtils.isNullOrEmptyOrBlank(studyDisplayTitle)) {
                doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TITLE_REGISTRY_ENTRY;
            } else {
                doDisplayTitle = ConverterCVT.O_TITLE_REGISTRY_ENTRY;
            }

            /* Registry entry DO */
            Item doRegistryEntry = this.createAndStoreClassItem(study, "DataObject", 
                new String[][]{{"title", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY}, {"displayTitle", doDisplayTitle}, 
                                {"objectClass", ConverterCVT.O_CLASS_TEXT}, {"objectType", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY}, 
                                {"publicationYear", publicationYear}});

            /* Registry entry DO instance */
            this.createAndStoreClassItem(doRegistryEntry, "ObjectInstance", 
                new String[][]{{"url", entryUrl}, {"resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT}});

            // Registry entry created date
            // TODO: format is usually dd/mm/yyyy but can also be mm-dd-yyyy
            // TODO: created or made available?
            if (registrationDate != null) {
                this.createAndStoreObjectDate(doRegistryEntry, registrationDate, ConverterCVT.DATE_TYPE_CREATED);
            }

            // Last update
            if (lastUpdate != null) {
                this.createAndStoreObjectDate(doRegistryEntry, lastUpdate, ConverterCVT.DATE_TYPE_UPDATED);
            }
        } else {
            // Update DO creation date
            if (registrationDate != null) {
                Item doRegistryEntry = this.getItemFromItemMap(study, this.objects, "objectType", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY);
                if (doRegistryEntry != null) {
                    Item creationOD = this.getItemFromItemMap(doRegistryEntry, this.objectDates, "dateType", ConverterCVT.DATE_TYPE_CREATED);
                    if (creationOD != null) {
                        String existingDateStr = ConverterUtils.getValueOfItemAttribute(creationOD, "startDate");
                        // Updating creation date if older than known creation date
                        if (!ConverterUtils.isNullOrEmptyOrBlank(existingDateStr)
                            && registrationDate.compareTo(ConverterUtils.getDateFromString(existingDateStr, null)) < 0) {
                                creationOD.setAttribute("startDate", registrationDate.toString());
                            this.writeLog("older creation date: " + registrationDate.toString() + ", previous: " + existingDateStr + " -country: " + this.currentCountry);
                        }
                    }
                }
            }

            // Update DO last update date
            if (lastUpdate != null) {
                Item doRegistryEntry = this.getItemFromItemMap(study, this.objects, "objectType",  ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY);
                if (doRegistryEntry != null) {
                    Item lastUpdateOD = this.getItemFromItemMap(doRegistryEntry, this.objectDates, "dateType", ConverterCVT.DATE_TYPE_UPDATED);
                    if (lastUpdateOD != null) {
                        String existingDateStr = ConverterUtils.getValueOfItemAttribute(lastUpdateOD, "startDate");
                        if (!ConverterUtils.isNullOrEmptyOrBlank(existingDateStr)
                            && lastUpdate.compareTo(ConverterUtils.getDateFromString(existingDateStr, null)) > 0) {
                                lastUpdateOD.setAttribute("startDate", lastUpdate.toString());
                            // TODO: newerLastUpdate logic in separate function?
                            this.newerLastUpdate = true;
                            this.writeLog("newer last update: " + lastUpdate.toString() + ", previous: " + existingDateStr + " -country: " + this.currentCountry);
                        }
                    }
                }
            }
        }
    }

    /**
     * TODO
     */
    public void parseGender(Item study, String genderStr) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(genderStr) && this.existingStudy()) {
            Matcher mGenderAll = P_GENDER_ALL.matcher(genderStr);
            if (mGenderAll.matches()) {
                study.setAttributeIfNotNull("studyGenderElig", ConverterCVT.GENDER_ALL);
            } else {    // "structured" pattern

                Matcher mGenderStructured = P_GENDER_STRUCTURED.matcher(genderStr);
                if (mGenderStructured.matches()) {
                    this.setGenderFromMatches(study, mGenderStructured, genderStr, ConverterCVT.GENDER_WOMEN, ConverterCVT.GENDER_MEN, true);
                } else {    // "Non-structured" pattern

                    Matcher mGenderMF = P_GENDER_MF.matcher(genderStr);
                    if (mGenderMF.matches()) {
                        this.setGenderFromMatches(study, mGenderMF, genderStr, ConverterCVT.GENDER_MEN, ConverterCVT.GENDER_WOMEN, false);
                    } else {

                        Matcher mGenderFM = P_GENDER_FM.matcher(genderStr);
                        if (mGenderFM.matches()) {
                            this.setGenderFromMatches(study, mGenderFM, genderStr, ConverterCVT.GENDER_WOMEN, ConverterCVT.GENDER_MEN, false);
                        } else {    // Not specified
                        
                            Matcher mGenderNotSpecified = P_UNKNOWN.matcher(genderStr);
                            if (mGenderNotSpecified.matches()) {
                                study.setAttributeIfNotNull("studyGenderElig", ConverterCVT.UNKNOWN);
                            } else {    // Raw value (shouldn't happen)

                                study.setAttributeIfNotNull("studyGenderElig", genderStr);
                                this.writeLog("Couldn't parse study gender (used raw value): " + genderStr);
                            }
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
            study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_ALL);
        } else if (g1 != null && (!structured || g1.equalsIgnoreCase("yes"))) {
            study.setAttributeIfNotNull("genderElig", gender1);
        } else if (g2 != null && (!structured || g2.equalsIgnoreCase("yes"))) {
            study.setAttributeIfNotNull("genderElig", gender2);
        } else {
            this.writeLog("Match for study gender (structured regex) but no gender found, string: " + genderStr);
        }
    }

    /**
     * TODO
     */
    public void parseIEC(Item study, String icStr, String ecStr) {
        if (!this.existingStudy()) {
            StringBuilder iec = new StringBuilder();

            /* Inclusion criteria */
            if (!ConverterUtils.isNullOrEmptyOrBlank(icStr)) {    // None
                Matcher mIcNone = P_IEC_NONE.matcher(icStr);
                if (mIcNone.matches()) {
                    String g1None = mIcNone.group(2);
                    if (!ConverterUtils.isNullOrEmptyOrBlank(g1None)) {
                        iec.append(IC_PREFIX + ConverterCVT.NONE);
                    } else {
                        iec.append(IC_PREFIX + ConverterCVT.UNKNOWN);
                    }
                } else {    // N/A
                    Matcher mIcNA = P_IEC_NA.matcher(icStr);
                    if (mIcNA.matches()) {
                        iec.append(IC_PREFIX + ConverterCVT.NOT_APPLICABLE);
                    } else {    // Raw value
                        iec.append(icStr);
                    }
                }
            }

            /* Exclusion criteria */
            if (!ConverterUtils.isNullOrEmptyOrBlank(ecStr)) {    // None
                Matcher mEcNone = P_IEC_NONE.matcher(ecStr);

                String adaptedEcPrefix = "";
                if (iec.length() > 0) {
                    adaptedEcPrefix = ". " + EC_PREFIX;
                } else {
                    adaptedEcPrefix = EC_PREFIX;
                }

                if (mEcNone.matches()) {
                    String g2None = mEcNone.group(2);
                    if (!ConverterUtils.isNullOrEmptyOrBlank(g2None)) {
                        iec.append(adaptedEcPrefix + ConverterCVT.NONE);
                    } else {
                        iec.append(adaptedEcPrefix + ConverterCVT.UNKNOWN);
                    }
                } else {    // N/A
                    Matcher mEcNA = P_IEC_NA.matcher(ecStr);
                    if (mEcNA.matches()) {
                        iec.append(adaptedEcPrefix + ConverterCVT.NOT_APPLICABLE);
                    } else {    // Raw value
                        iec.append(ecStr);
                    }
                }
            }

            // Setting IEC string constructed from IC + EC
            String iecStr = iec.toString();
            if (!ConverterUtils.isNullOrEmptyOrBlank(iecStr)) {
                study.setAttributeIfNotNull("iec", iecStr);
            }
        }
    }

    public void parseDataSharingStatement(Item study, String resultsIPDPlan, String resultsIPDDescription) {
        if (!this.existingStudy()) {
            StringBuilder dSSBuilder = new StringBuilder();

            if (!ConverterUtils.isNullOrEmptyOrBlank(resultsIPDPlan)) {
                dSSBuilder.append(resultsIPDPlan);
                if (!resultsIPDPlan.endsWith(".")) {
                    dSSBuilder.append(".");
                }
                dSSBuilder.append(" ");
            }

            if (!ConverterUtils.isNullOrEmptyOrBlank(resultsIPDDescription)) {
                dSSBuilder.append(resultsIPDDescription);
            }

            String dataSharingStatement = dSSBuilder.toString();
            if (!ConverterUtils.isNullOrEmptyOrBlank(dataSharingStatement)) {
                study.setAttributeIfNotNull("dataSharingStatement", dataSharingStatement);
            }
        }
    }

    /**
     * Get field value from array of values using a field's position-lookup Map, value is also cleaned.
     * 
     * @param lineValues the list of all values for a line in the data file
     * @param field the name of the field to get the value of
     * @return the cleaned value of the field
     * //@see cleanValue()
     */
    public String getAndCleanValue(String[] lineValues, String field) {
        // TODO: handle errors
        return this.cleanValue(lineValues[this.fieldsToInd.get(field)], true);
    }

    /**
     * Get field value from array of values using a field's position-lookup Map, value is also cleaned without stripping it of leading/trailing whitespaces.
     * 
     * @param lineValues the list of all values for a line in the data file
     * @param field the name of the field to get the value of
     * @return the cleaned value of the field
     * //@see #cleanValue()
     */
    public String getAndCleanValueNoStrip(String[] lineValues, String field) {
        // TODO: handle errors
        return this.cleanValue(lineValues[this.fieldsToInd.get(field)], false);
    }

    /**
     * Remove extra quotes, unescape HTML chars, and strip the string of any leading/trailing whitespace.
     * 
     * @param s the value to clean
     * @return the cleaned value
     * //@see #unescapeHtml()
     * //@see #removeQuotes()
     */
    public String cleanValue(String s, boolean strip) {
        if (strip) {
            return ConverterUtils.unescapeHtml(ConverterUtils.removeQuotes(s)).strip();
        }
        return ConverterUtils.unescapeHtml(ConverterUtils.removeQuotes(s));
    }

    public boolean isEuctr() {
        return this.registry.equals(ConverterCVT.R_EUCTR);
    }

    /**
     * TODO
     */
    public Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item item = this.createClassItem(mainClassItem, className, kv);

        if (item != null) {
            if (this.isEuctr()) {
                // Get item map name from reference
                String mapName = this.getReverseReferenceNameOfClass(className);
                
                // Get item map name from collection
                if (mapName == null) {
                    mapName = this.getReverseCollectionNameOfClass(className, mainClassItem.getClassName());
                }

                if (mapName != null) {
                    Map<String, List<Item>> itemMap = (Map<String, List<Item>>) WhoConverter.class.getSuperclass().getDeclaredField(mapName).get(this);
                    if (itemMap != null) {
                        this.saveToItemMap(mainClassItem, itemMap, item);
                    } else {
                        this.writeLog("Couldn't save EUCTR item to map, class name: " + className);
                    }
                } else {
                    this.writeLog("Failed to save EUCTR item to map (couldn't find map), class name: " + className);
                }
            } else {
                store(item);
            }
        } else {
            this.writeLog("Failed to create item of class " + className + ", attributes: " + kv);
        }

        return item;
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
