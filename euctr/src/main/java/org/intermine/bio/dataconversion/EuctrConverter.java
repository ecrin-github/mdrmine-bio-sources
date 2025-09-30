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

 import javax.xml.stream.XMLInputFactory;
 import javax.xml.stream.XMLStreamReader;
 import javax.xml.stream.events.XMLEvent;
 import java.io.Reader;
 import java.lang.reflect.Method;
 import java.time.LocalDate;
 import java.util.*;
 import java.util.regex.Matcher;
 import java.util.regex.Pattern;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.commons.text.WordUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;



/**
 * 
 * Explanation on some of the fields here: https://www.clinicaltrialsregister.eu/doc/How_to_Search_EU_CTR.pdf
 * @author
 */
public class EuctrConverter extends CacheConverter
{
    private static final String DATASET_TITLE = "EUCTR_allfile";
    private static final String DATA_SOURCE_NAME = "EUCTR";

    private static final Pattern P_TITLE_NA = Pattern.compile("^-|_|N\\/?A$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_HC_CODE = Pattern.compile("\\h*therapeutic\\h*area:\\h*(.*)\\h+\\[(\\w+)\\]\\h*-\\h*(.*)\\h+\\[(\\w+)\\]\\h*", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_HC_KEYWORD = Pattern.compile(".*classification\\h*code\\h*(\\w+).*term:?\\h*([^\\n]+).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern P_PHASE = Pattern.compile(
        ".*Phase\\h*i\\):\\h*(no|yes|)?\\n.*\\(Phase\\h*ii\\):\\h*(no|yes|)?\\n.*\\(Phase\\h*iii\\):\\h*(no|yes|)?\\n.*\\(Phase\\h*iv\\):\\h*(no|yes|)?.*", 
        Pattern.CASE_INSENSITIVE);
    private static final Pattern P_GENDER = Pattern.compile(".*female:\\h*(yes|no|)\\h*\\nmale:\\h*(yes|no|).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern P_ID_NA = Pattern.compile("^\\h*(?:n[\\.\\/]?(?:a|d)\\.?|not\\h*.*|-+|none)\\h*$", Pattern.CASE_INSENSITIVE);   // N/A (all forms) or - or none
    private static final Set<String> dummyIDs = Set.of("ISRCTN00000000", "NCT00000000", "ISRCTN12345678", "NCT12345678");

    private static final String FEATURE_YES = "yes";
    private static final String FEATURE_NO = "no";
    private static final String ISS_AUTH_PROTOCOL_CODE = "Sponsor Protocol Code";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public EuctrConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        this.startLogging("euctr");

        XMLInputFactory xi = XMLInputFactory.newInstance();
        XMLStreamReader xr = xi.createXMLStreamReader(reader);
        XmlMapper xm = new XmlMapper();
        int eventType;

        while (xr.hasNext()) {
            eventType = xr.next();
            switch (eventType) {
                case XMLEvent.START_ELEMENT:
                    if (xr.getLocalName().toLowerCase().equals("trial")) {
                        EuctrTrial trial = xm.readValue(xr, EuctrTrial.class);
                        this.parseAndStoreTrial(trial);
                    }
                    break;
                case XMLEvent.CHARACTERS:
                    break;
                case XMLEvent.ATTRIBUTE:
                    break;
                case XMLEvent.START_DOCUMENT:
                    break;
                default:
                    break;
            }
        }
        xr.close();

        this.storeAllItems();

        this.stopLogging();
        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    /**
     * TODO
     */
    public void parseAndStoreTrial(EuctrTrial trial) throws Exception {
        EuctrMainInfo mainInfo = trial.getMainInfo();
        if (mainInfo == null) {
            this.writeLog("mainInfo is null");
        } else {
            Item study = null;
            this.newerLastUpdate = false;
            this.existingStudy = null;
            this.currentCountry = null;

            String mainId = this.getAndCleanValue(mainInfo, "trialId");
            
            // Handling ID
            // TODO: handle "Outside-EU/EEA" ID suffix
            if (mainId.length() != 17) {
                this.writeLog("Unexpected length for study id: " + mainId);
            } else {
                // ID without country code suffix
                String cleanedID = mainId.substring(0, 14);
                String countryCode = mainId.substring(15, 17);

                // Adding EUCTR prefix
                // cleanedID = "EUCTR" + cleanedID;
                
                if (this.studies.containsKey(cleanedID)) {   // Adding country-specific info to existing trial
                    this.existingStudy = this.studies.get(cleanedID);
                    study = this.existingStudy;
                } else {
                    study = createItem("Study");
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

                /* Study data source */
                this.createAndStoreClassItem(study, "StudySource", new String[][]{{"sourceName", ConverterCVT.SOURCE_NAME_EUCTR}});
    
                /* EUCTR trial ID */
                String trialUrl = this.getAndCleanValue(mainInfo, "url");
                // TODO: also add ID with suffix?
                if (!this.existingStudy()) {
                    // study.setAttributeIfNotNull("primaryIdentifier", this.currentTrialID);
                    study.setAttributeIfNotNull("euctrID", this.currentTrialID);
                    this.createAndStoreStudyIdentifier(study, this.currentTrialID, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, trialUrl);
                }
                
                // TODO: make title object
                /* Study title (need to get it before registry entry DO) */
                String publicTitle = this.getAndCleanValue(mainInfo, "publicTitle");
                String scientificTitle = this.getAndCleanValue(mainInfo, "scientificTitle");
                String scientificAcronym = this.getAndCleanValue(mainInfo, "scientificAcronym");
                if (!this.existingStudy()) {
                    this.parseTitles(study, publicTitle, scientificTitle, scientificAcronym);
                }
    
                /* WHO universal trial number */
                String trialUtrn = this.getAndCleanValue(mainInfo, "utrn");
                // TODO: identifier type?
                // TODO: also gives duplicate errors
                if (!this.existingStudy()) {
                    this.createAndStoreStudyIdentifier(study, trialUtrn, null, null);
                }
    
                // Unused, name of registry, seems to always be EUCTR
                String regName = this.getAndCleanValue(mainInfo, "regName");
    
                // "Date on which this record was first entered in the EudraCT database" (from trials-full.txt dat format)
                // Using this as "newer update" date
                // TODO: more relevant to use date enrolment?
                String dateRegistrationStr = this.getAndCleanValue(mainInfo, "dateRegistration");
                LocalDate dateRegistration = this.parseDate(dateRegistrationStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
    
                /* Trial registry entry DO */
                this.createAndStoreRegistryEntryDO(study, dateRegistration, trialUrl);
    
                /* Study people: primary sponsor */
                String primarySponsor = this.getAndCleanValue(mainInfo, "primarySponsor");
                this.parsePrimarySponsor(study, primarySponsor);
    
                // Unused, always empty
                String acronym = this.getAndCleanValue(mainInfo, "acronym");
    
                /* Date enrolment (start date), also seems to be "Date of Ethics Committee Opinion" */
                String dateEnrolmentStr = this.getAndCleanValue(mainInfo, "dateEnrolment");
                LocalDate dateEnrolment = this.parseDate(dateEnrolmentStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
                this.setStudyStartDate(study, dateEnrolment);
                if (dateEnrolment != null) {    // (?)
                    // study.setAttributeIfNotNull("testField1", "EUCTR_" + (dateEnrolment != null ? dateEnrolment.toString() : null));
                }
    
                // Unused, "Date trial authorised"
                String typeEnrolment = this.getAndCleanValue(mainInfo, "typeEnrolment");
    
                /* Study planned enrolment (for the whole trial) */
                String targetSize = this.getAndCleanValue(mainInfo, "targetSize");
                this.setPlannedEnrolment(study, targetSize);
    
                // "Not Recruiting" or "Authorised-recruitment may be ongoing or finished" or NA
                String recruitmentStatus = this.getAndCleanValue(mainInfo, "recruitmentStatus");
                if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(recruitmentStatus) && !recruitmentStatus.equalsIgnoreCase("NA")) {
                    study.setAttributeIfNotNull("status", recruitmentStatus);
                }
                
                /* Note: from https://www.clinicaltrialsregister.eu/about.html, we can read: 
                * The EU Clinical Trials Register does not: 
                * provide information on non-interventional clinical trials of medicines (observational studies on authorised medicines);
                * provide information on clinical trials for surgical procedures, medical devices or psychotherapeutic procedures; 
                */
                // This is always "Interventional clinical trial of medicinal product"
                // String studyType = this.getAndCleanValue(mainInfo, "studyType");
                if (!this.existingStudy()) {
                    study.setAttributeIfNotNull("type", ConverterCVT.TYPE_INTERVENTIONAL);
                }
                
                /* Study features */
                String studyDesign = this.getAndCleanValue(mainInfo, "studyDesign");
                // study.setAttributeIfNotNull("testField3", "EUCTR_" + studyDesign);
                this.parseStudyDesign(study, studyDesign);
                
                /* Study feature: phase */
                String phase = this.getAndCleanValue(mainInfo, "phase");
                this.parsePhase(study, phase);
                
                /* Study condition */
                String hcFreetext = this.getAndCleanValue(mainInfo, "hcFreetext");
                List<String> hcCodes = trial.getHealthConditionCodes();
                List<String> hcKeywords = trial.getHealthConditionKeywords();
                this.parseHealthConditions(study, hcFreetext, hcCodes, hcKeywords);
                
                /* Study topic: products */
                String iFreetext = this.getAndCleanValue(mainInfo, "iFreetext");
                // Unused, always empty
                List<String> iCodes = trial.getInterventionCodes();
                // Unused, always empty
                List<String> iKeywords = trial.getInterventionKeywords();
                this.parseInterventions(study, iFreetext);
                
                /* Study actual enrolment (for the whole trial) */
                String resultsActualEnrolment = this.getAndCleanValue(mainInfo, "resultsActualEnrolment");
                this.setActualEnrolment(study, resultsActualEnrolment);
                
                /* Study end date ("global completion date") */
                String resultsDateCompletedStr = this.getAndCleanValue(mainInfo, "resultsDateCompleted");
                // study.setAttributeIfNotNull("testField4", resultsDateCompletedStr);
                LocalDate resultsDateCompleted = this.parseDate(resultsDateCompletedStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
                this.setStudyEndDate(study, resultsDateCompleted);
                
                // Study results page link
                String resultsUrlLink = this.getAndCleanValue(mainInfo, "resultsUrlLink");
                
                // Results summary here is actually more of a general trial summary than a results summary
                String resultsSummary = this.getAndCleanValue(mainInfo, "resultsSummary");
                study.setAttributeIfNotNull("briefDescription", resultsSummary);
                
                String resultsDatePostedStr = this.getAndCleanValue(mainInfo, "resultsDatePosted");
                LocalDate resultsDatePosted = this.parseDate(resultsDatePostedStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
                
                /* Results summary DO */
                this.createAndStoreResultsSummaryDO(study, resultsUrlLink, resultsDateCompleted, resultsDatePosted);
                
                // Unused, always empty
                String resultsDateFirstPublication = this.getAndCleanValue(mainInfo, "resultsDateFirstPublication");
                // Unused (Link to results page section)
                String resultsBaselineChar = this.getAndCleanValue(mainInfo, "resultsBaselineChar");
                // Unused (Link to results page section)
                String resultsParticipantFlow = this.getAndCleanValue(mainInfo, "resultsParticipantFlow");
                // Unused (Link to results page section)
                String resultsAdverseEvents = this.getAndCleanValue(mainInfo, "resultsAdverseEvents");
                // Unused (Link to results page section)
                String resultsOutcomeMeasures = this.getAndCleanValue(mainInfo, "resultsOutcomeMeasures");
                // Unused, always empty
                String resultsUrlProtocol = this.getAndCleanValue(mainInfo, "resultsUrlProtocol");
                // Unused, always empty
                String resultsIPDPlan = this.getAndCleanValue(mainInfo, "resultsIPDPlan");
                // Unused, always empty
                String resultsIPDDescription = this.getAndCleanValue(mainInfo, "resultsIPDDescription");

                /* People */
                List<EuctrContact> contacts = trial.getContacts();
                this.parseContacts(study, contacts);

                /* Study countries */
                List<String> countries = trial.getCountries();
                this.parseCountries(study, countries);

                // Trial IEC
                EuctrCriteria criteria = trial.getCriteria();

                /* IEC */
                String ic = criteria.getInclusionCriteria();
                String ec = criteria.getExclusionCriteria();
                this.parseIEC(study, ic, ec);

                /* Gender */
                String gender = criteria.getGender();
                this.parseGender(study, gender);

                // Unused, always empty
                String ageMin = criteria.getAgemin();
                // Unused, always empty
                String ageMax = criteria.getAgemax();

                /* Primary outcomes */
                List<String> primaryOutcomes = trial.getPrimaryOutcomes();
                this.parseOutcomes(study, primaryOutcomes, "primary");
                
                /* Secondary outcomes */
                List<String> secondaryOutcomes = trial.getSecondaryOutcomes();
                this.parseOutcomes(study, secondaryOutcomes, "secondary");
                
                /* Secondary sponsors */
                List<String> secondarySponsors = trial.getSecondarySponsors();
                this.parseSecondarySponsors(study, secondarySponsors);

                List<EuctrSecondaryId> secondaryIDs = trial.getSecondaryIds();
                // TODO: partially unused because of multiple IDs problem
                this.parseSecondaryIDs(study, secondaryIDs);

                List<String> sourceSupport = trial.getSourceSupport();
                this.parseSourceSupports(study, sourceSupport);
                
                List<EuctrEthicsReview> ethicsReviews = trial.getEthicsReviews();
                this.parseEthicsReviews(study, ethicsReviews);

                // Storing in cache
                if (!this.existingStudy()) {
                    this.studies.put(this.currentTrialID, study);
                }
    
                this.currentTrialID = null;
            }
        }
    }

    /** */
    public void parseTitles(Item study, String publicTitle, String scientificTitle, String scientificAcronym) throws Exception {
        if (!this.existingStudy()) {
            boolean displayTitleSet = false;
            // TODO: only 1 matcher object?
            /* Public title */
            Matcher mPublicTitleNA = P_TITLE_NA.matcher(publicTitle);
            if (!mPublicTitleNA.matches()) {
                study.setAttributeIfNotNull("displayTitle", publicTitle);
                displayTitleSet = true;

                this.createAndStoreClassItem(study, "Title", 
                new String[][]{{"text", publicTitle}, {"type", ConverterCVT.TITLE_TYPE_PUBLIC}});
            }
            
            /* Scientific title */
            Matcher mScientificTitleNA = P_TITLE_NA.matcher(scientificTitle);
            if (!mScientificTitleNA.matches()) {
                if (!displayTitleSet) {
                    study.setAttributeIfNotNull("displayTitle", scientificTitle);
                    displayTitleSet = true;
                }

                this.createAndStoreClassItem(study, "Title", 
                    new String[][]{{"text", scientificTitle}, {"type", ConverterCVT.TITLE_TYPE_SCIENTIFIC}});
            }

            /* Acronym */
            Matcher mScientificAcronymNA = P_TITLE_NA.matcher(scientificAcronym);
            if (!mScientificAcronymNA.matches()) {
                if (!displayTitleSet) {
                    study.setAttributeIfNotNull("displayTitle", scientificAcronym);
                    displayTitleSet = true;
                }

                this.createAndStoreClassItem(study, "Title", 
                    new String[][]{{"text", scientificAcronym}, {"type", ConverterCVT.TITLE_TYPE_ACRONYM}});
            }

            // Unknown title if not set before
            if (!displayTitleSet) {
                study.setAttributeIfNotNull("displayTitle", ConverterCVT.TITLE_UNKNOWN);
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param primarySponsor
     * @throws Exception
     */
    public void parsePrimarySponsor(Item study, String primarySponsor) throws Exception {
        if (!this.existingStudy()) {
            this.createAndStoreClassItem(study, "Organisation",
                new String[][]{{"contribType", ConverterCVT.CONTRIBUTOR_TYPE_SPONSOR}, 
                                {"name", primarySponsor}});
        }
    }

    /**
     * TODO
     * @param study
     * @param creationDate
     * @param url
     * @throws Exception
     */
    public void createAndStoreRegistryEntryDO(Item study, LocalDate creationDate, String url) throws Exception {
        if (!this.existingStudy()) {
            String studyDisplayTitle = ConverterUtils.getValueOfItemAttribute(study, "displayTitle");
            String doDisplayTitle;
            if (!ConverterUtils.isNullOrEmptyOrBlank(studyDisplayTitle)) {
                doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TITLE_REGISTRY_ENTRY;
            } else {
                doDisplayTitle = ConverterCVT.O_TITLE_REGISTRY_ENTRY;
            }

            /* Trial registry entry DO */
            Item doRegistryEntry = this.createAndStoreClassItem(study, "DataObject", 
                new String[][]{{"type", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY}, {"objectClass", ConverterCVT.O_CLASS_TEXT},
                                {"title", doDisplayTitle}});

            /* Registry entry instance */
            if (!ConverterUtils.isNullOrEmptyOrBlank(this.currentTrialID)) {
                this.createAndStoreClassItem(doRegistryEntry, "ObjectInstance", 
                    new String[][]{{"url", url}, {"resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT}});
            }

            /* Object created date */
            // TODO: available?
            this.createAndStoreObjectDate(doRegistryEntry, creationDate, ConverterCVT.DATE_TYPE_CREATED);
        } else {
            // Update DO creation date
            if (creationDate != null) {
                Item doRegistryEntry = this.getItemFromItemMap(study, this.objects, "type", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY);
                if (doRegistryEntry != null) {
                    Item creationOD = this.getItemFromItemMap(doRegistryEntry, this.objectDates, "dateType", ConverterCVT.DATE_TYPE_CREATED);
                    if (creationOD != null) {
                        String existingDateStr = ConverterUtils.getValueOfItemAttribute(creationOD, "startDate");
                        // Updating creation date if older than known creation date
                        if (!ConverterUtils.isNullOrEmptyOrBlank(existingDateStr)
                            && creationDate.compareTo(ConverterUtils.getDateFromString(existingDateStr, null)) < 0) {
                                creationOD.setAttributeIfNotNull("startDate", creationDate.toString());
                            // Using record registration date as "newer last update"
                            // TODO: use a different variable (name)?
                            this.newerLastUpdate = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param plannedEnrolment
     */
    public void setPlannedEnrolment(Item study, String plannedEnrolment) {
        if (ConverterUtils.isPosWholeNumber(plannedEnrolment) && !(Long.valueOf(plannedEnrolment) > Integer.MAX_VALUE)) {
            // study.setAttributeIfNotNull("testField2", "EUCTR_" + plannedEnrolment);
            if (!this.existingStudy() || this.newerLastUpdate) {    // Updating planned enrolment for more recent record registration date
                study.setAttributeIfNotNull("plannedEnrolment", plannedEnrolment);
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param actualEnrolment
     */
    public void setActualEnrolment(Item study, String actualEnrolment) {
        if (ConverterUtils.isPosWholeNumber(actualEnrolment) && !(Long.valueOf(actualEnrolment) > Integer.MAX_VALUE)) {
            if (!this.existingStudy() || this.newerLastUpdate) {    // Updating actual enrolment for more recent record registration date
                study.setAttributeIfNotNull("actualEnrolment", actualEnrolment);
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param studyFeaturesStr
     * @throws Exception
     */
    public void parseStudyDesign(Item study, String studyFeaturesStr) throws Exception {
        /*
         * Controlled: yes -> ?
            Randomised: yes 
            Open: yes 
            Single blind: no 
            Double blind: no 
            Parallel group: yes 
            Cross over: no 
            Other: yes -> ?
            Other trial design description: option to cross over from Nivo to Nivo/ipi combination for those that progress on monotherapy arm 
            If controlled, specify comparator, Other Medicinial Product: no 
            Placebo: no -> ?
            Other: yes -> ?
            Other specify the comparator: each arm in each tumor type will be evaluated independently 
            Number of treatment arms in the trial: 4 -> ?
         */

        if (!this.existingStudy()) {
            String[] splitFeatures = studyFeaturesStr.split("\n");
    
            if (splitFeatures.length == 0) {
                this.writeLog("Failed to split features by newline");
            }
    
            // Processing feature-value tuples
            boolean maskingSet = false;
            boolean interventionSet = false;
    
            for (int i = 0; i < splitFeatures.length; i++) {
                String[] tuple = splitFeatures[i].split(": ");
                if (tuple.length == 0) {
                    this.writeLog("Failed to split feature-value tuple with colon: " + String.join(";", tuple));
                } else if (tuple.length == 1) {
                    // TODO: means value is probably empty (=no)
                    this.writeLog("Split feature-value tuple with colon but got only 1 value: " + String.join(";", tuple));
                } else if (tuple.length > 2) {
                    this.writeLog("Split feature-value tuple with colon but got more than 2 values: " + String.join(";", tuple));
                } else if (!ConverterUtils.isNullOrEmptyOrBlank(tuple[1])) {
                    switch(tuple[0].toLowerCase()) {
                        case "controlled":
                            // TODO?
                            break;
                        case "randomised":
                            if (tuple[1].toLowerCase().equals(FEATURE_YES)) {
                                this.createAndStoreClassItem(study, "StudyFeature", 
                                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_ALLOCATION},
                                                    {"featureValue", ConverterCVT.FEATURE_V_RANDOMISED}});
                            } else if (tuple[1].toLowerCase().equals(FEATURE_NO)) {
                                this.createAndStoreClassItem(study, "StudyFeature", 
                                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_ALLOCATION},
                                                    {"featureValue", ConverterCVT.FEATURE_V_NONRANDOMISED}});
                            } else {
                                this.writeLog("Unknown study design randomised value: " + tuple[1]);
                            }
                            break;
                        case "open":
                            if (tuple[1].toLowerCase().equals(FEATURE_YES)) {
                                if (!maskingSet) {
                                    this.createAndStoreClassItem(study, "StudyFeature", 
                                        new String[][]{{"featureType", ConverterCVT.FEATURE_T_MASKING},
                                                        {"featureValue", ConverterCVT.FEATURE_V_NO_BLINDING}});
                                    maskingSet = true;
                                } else {
                                    this.writeLog("Study feature of type masking - open has value yes but masking has already been set");
                                }
                            }
                            break;
                        case "single blind":
                            if (tuple[1].toLowerCase().equals(FEATURE_YES)) {
                                if (!maskingSet) {
                                    this.createAndStoreClassItem(study, "StudyFeature", 
                                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_MASKING},
                                                        {"featureValue", ConverterCVT.FEATURE_V_SINGLE_BLIND}});
                                    maskingSet = true;
                                } else {
                                    this.writeLog("Study feature of type masking - single blind has value yes but masking has already been set");
                                }
                            }
                            break;
                        case "double blind":
                            if (tuple[1].toLowerCase().equals(FEATURE_YES)) {
                                if (!maskingSet) {
                                    this.createAndStoreClassItem(study, "StudyFeature", 
                                        new String[][]{{"featureType", ConverterCVT.FEATURE_T_MASKING},
                                                        {"featureValue", ConverterCVT.FEATURE_V_DOUBLE_BLIND}});
                                    maskingSet = true;
                                } else {
                                    this.writeLog("Study feature of type masking - double blind has value yes but masking has already been set");
                                }
                            }
                            break;
                        case "parallel group":
                            if (tuple[1].toLowerCase().equals(FEATURE_YES)) {
                                if (!interventionSet) {
                                    this.createAndStoreClassItem(study, "StudyFeature", 
                                        new String[][]{{"featureType", ConverterCVT.FEATURE_T_INTERVENTION_MODEL},
                                                        {"featureValue", ConverterCVT.FEATURE_V_PARALLEL}});
                                    interventionSet = true;
                                } else {
                                    this.writeLog("Study feature of type intervention model - parallel has value yes but masking has already been set");
                                }
                            }
                            break;
                        case "cross over":
                            if (tuple[1].toLowerCase().equals(FEATURE_YES)) {
                                if (!interventionSet) {
                                    this.createAndStoreClassItem(study, "StudyFeature", 
                                        new String[][]{{"featureType", ConverterCVT.FEATURE_T_INTERVENTION_MODEL},
                                                        {"featureValue", ConverterCVT.FEATURE_V_CROSSOVER}});
                                    interventionSet = true;
                                } else {
                                    this.writeLog("Study feature of type intervention model - cross over has value yes but masking has already been set");
                                }
                            }
                            break;
                        case "other":
                            // TODO?
                            break;
                        case "other trial design description":
                            // TODO?
                            break;
                        case "if controlled, specify comparator, other medicinial product":
                            // TODO?
                            break;
                        case "placebo":
                            // TODO?
                            break;
                        case "other specify the comparator":
                            // TODO?
                            break;
                        case "number of treatment arms in the trial":
                            // TODO?
                            break;
                        default:
                            this.writeLog("Unknown feature type: " + tuple[0].toLowerCase());
                    }
                }
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param phaseStr
     * @throws Exception
     */
    public void parsePhase(Item study, String phaseStr) throws Exception {
        if (!this.existingStudy()) {
            String phaseValue = null;

            Matcher mPhaseVerbose = P_PHASE.matcher(phaseStr);
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
                            phaseValue = "Phase " + String.valueOf(phasesRes.get(0));
                        } else if (phasesRes.size() == 2) { // Two phases
                            phaseValue = "Phase " + String.valueOf(phasesRes.get(0)) + "/" + String.valueOf(phasesRes.get(1));
                        } else {
                            this.writeLog("Matched more than 2 groups for phase string, g1: " + p1 + "; g2: " + p2 + "; g3: " + p3 + ", full string: " + phaseStr);
                        }
                    } else {
                        this.writeLog("Matched phase string but all values are no/empty, g1: " + p1 + "; g2: " + p2 + "; g3: " + p3 + ", full string: " + phaseStr);
                    }
                } else {
                    this.writeLog("Matched but failed to properly parse phase string, g1: " + p1 + "; g2: " + p2 + "; g3: " + p3 + ", full string: " + phaseStr);
                }
            } else {
                // Using raw value
                phaseValue = phaseStr;
            }

            if (!ConverterUtils.isNullOrEmptyOrBlank(phaseValue)) {
                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_PHASE}, {"featureValue", phaseValue}});
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param hcFreetext
     * @param hcCodes
     * @param hcKeywords
     * @throws Exception
     */
    public void parseHealthConditions(Item study, String hcFreetext, List<String> hcCodes, List<String> hcKeywords) throws Exception {
        if (!this.existingStudy()) {
            // Free text
            if (!ConverterUtils.isNullOrEmptyOrBlank(hcFreetext)) {
                this.createAndStoreClassItem(study, "StudyCondition", 
                    new String[][]{{"originalValue", WordUtils.capitalizeFully(hcFreetext, ' ', '-')}});
            }

            // HC Codes (MeSH tree)
            Matcher mHcCode;
            for (String hcCode: hcCodes) {
                if (!ConverterUtils.isNullOrEmptyOrBlank(hcCode)) {
                    mHcCode = P_HC_CODE.matcher(hcCode);

                    if (mHcCode.matches()) {
                        // TODO: remove if too generic? therapeutic area (diseases/body conditions)
                        this.createAndStoreClassItem(study, "StudyCondition", 
                            new String[][]{{"originalValue", WordUtils.capitalizeFully(mHcCode.group(1), ' ', '-')},
                                            {"originalCTType", ConverterCVT.CV_MESH_TREE}, {"originalCTCode", mHcCode.group(2)}});

                        // More specific condition
                        this.createAndStoreClassItem(study, "StudyCondition", 
                            new String[][]{{"originalValue", WordUtils.capitalizeFully(mHcCode.group(3), ' ', '-')},
                                            {"originalCTType", ConverterCVT.CV_MESH_TREE}, {"originalCTCode", mHcCode.group(4)}});
                    } else {
                        this.writeLog("Failed to match health condition code: " + hcCode);
                    }
                }
            }

            // HC Keywords (MedDRA)
            Matcher mHcKeyword;
            for (String hcKw: hcKeywords) {
                if (!ConverterUtils.isNullOrEmptyOrBlank(hcKw)) {
                    mHcKeyword = P_HC_KEYWORD.matcher(hcKw);
    
                    if (mHcKeyword.matches()) {
                        this.createAndStoreClassItem(study, "StudyCondition", 
                            new String[][]{{"originalValue", WordUtils.capitalizeFully(mHcKeyword.group(2), ' ', '-')},
                                            {"originalCTType", ConverterCVT.CV_MEDDRA}, {"originalCTCode", mHcKeyword.group(1)}});
                    } else {
                        this.writeLog("Failed to match health condition keyword: " + hcKw);
                    }
                }
            }
        }
        // TODO: add check for new conditions for exisiting studies
    }

    /**
     * TODO
     * @param study
     * @param iFreetext
     * @throws Exception
     */
    public void parseInterventions(Item study, String iFreetext) throws Exception {
        // TODO: Relevant? doing same thing as in WHO
        study.setAttributeIfNotNull("interventions", iFreetext);
        
        // TODO: filter out "empty" products ("Pharmaceutical Form:")
        // TODO: match with CV
        String[] products = iFreetext.split("\n\n");
        for (String p: products) {
            if (!ConverterUtils.isNullOrEmptyOrBlank(p)) {
                this.createAndStoreClassItem(study, "Topic",
                    new String[][]{{"type", ConverterCVT.TOPIC_TYPE_CHEMICAL_AGENT}, {"value", p}});
            }
        }
    }

    
    /**
     * TODO
     * @param study
     * @param resultsUrlLink
     * @param resultsDateCompleted
     * @param resultsDatePosted
     * @throws Exception
     */
    public void createAndStoreResultsSummaryDO(Item study, String resultsUrlLink, LocalDate resultsDateCompleted, LocalDate resultsDatePosted) throws Exception {
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(resultsUrlLink) && resultsDatePosted != null) {
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
                                        new String[][]{{"title", doDisplayTitle}, {"objectClass", ConverterCVT.O_CLASS_TEXT}, 
                                                        {"type", ConverterCVT.O_TYPE_TRIAL_REGISTRY_RESULTS_SUMMARY}});
            /* Instance with results URL */
            // TODO: system? (=source)
            this.createAndStoreClassItem(resultsSummaryDO, "ObjectInstance", 
                                        new String[][]{{"url", resultsUrlLink}, {"resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT}});
            
            // Results completed date (trial end date)
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
     * @param study
     * @param contacts
     * @throws Exception
     */
    public void parseContacts(Item study, List<EuctrContact> contacts) throws Exception {
        if (!this.existingStudy()) {
            String type, firstName, affiliation;
            for (EuctrContact contact: contacts) {
                type = contact.getType();
                firstName = contact.getFirstname();
                affiliation = contact.getAffiliation();
                if (!ConverterUtils.isNullOrEmptyOrBlank(firstName)) {

                    if (!ConverterUtils.isNullOrEmptyOrBlank(type)) {
                        if (type.equalsIgnoreCase("public")) {
                            type = ConverterCVT.CONTRIBUTOR_TYPE_PUBLIC_CONTACT;
                        } else if (type.equalsIgnoreCase("scientific")) {
                            // Exception already thrown earlier so value can't be anything other than "scientific"
                            type = ConverterCVT.CONTRIBUTOR_TYPE_SCIENTIFIC_CONTACT;
                        } else {
                            this.writeLog("Unknown contact type value: " + type);
                        }
                    }

                    // TODO: add reference to organisation affiliation
                    // TODO: differentiate people from organisations
                    if (!firstName.equalsIgnoreCase("not applicable")) {
                        this.createAndStoreClassItem(study, "Person",
                            new String[][]{ {"fullName", firstName}, {"affiliation", affiliation}, {"contribType", type}});
                    }   // TODO: else
                }
            }
        }

        /*
        <contact>
            <type>Public</type>
            <firstname>Principal Investigator</firstname>
            <middlename />
            <lastname />
            <address>Liebigstrasse 10-14</address>
            <city>Leipzig</city>
            <country1>Germany</country1>
            <zip>04103</zip>
            <telephone>0049034197 21 650</telephone>
            <email>augen@medizin.uni-leipzig.de</email>
            <affiliation>University of Leipzig</affiliation>
        </contact>
         */

        /*
        <class name="Person" is-interface="true">
            <attribute name="contribType" type="java.lang.String"/>
            <attribute name="givenName" type="java.lang.String"/>
            <attribute name="familyName" type="java.lang.String"/>
            <attribute name="fullName" type="java.lang.String"/>
            <attribute name="affiliation" type="java.lang.String"/>
            <attribute name="orcid" type="java.lang.String"/>
            <collection name="studies" referenced-type="Study" reverse-reference="people"/>
            <collection name="objects" referenced-type="DataObject" reverse-reference="people"/>
            <collection name="affiliations" referenced-type="Organisation" reverse-reference="people"/>
        </class>
        */
    }

    /**
     * TODO
     * @param study
     * @param countries
     * @throws Exception
     */
    public void parseCountries(Item study, List<String> countries) throws Exception {
        // TODO: don't add duplicates (EUCTR2008-007326-19)
        String status = ConverterUtils.getValueOfItemAttribute(study, "status");

        if (!this.existingStudy() && countries.size() > 0) {
            boolean foundCurrentCountry = false;

            for (String countryName: countries) {
                if (!ConverterUtils.isNullOrEmptyOrBlank(countryName)) {
                    if (this.currentCountry != null && countryName.equalsIgnoreCase(this.currentCountry.getName())) {    // Setting more info for current country
                        this.createAndStoreStudyCountry(study, countryName, status, null, null, null);
                        // this.createAndStoreStudyCountry(study, countryName, status, plannedEnrolment, cadDate, null);
                        foundCurrentCountry = true;
                    } else {    // Regular parsing
                        this.createAndStoreStudyCountry(study, countryName, null, null, null, null);
                    }
                }
            }

            // Creating country if currentCountry is set but couldn't match it with a country in the country list
            if (this.currentCountry != null && !foundCurrentCountry) {
                this.createAndStoreStudyCountry(study, this.currentCountry.getName(), status, null, null, null);
                // this.createAndStoreStudyCountry(study, this.currentCountry.getName(), status, plannedEnrolment, cadDate, null);
            }
        } else if (this.currentCountry != null) {   // Updating existing study
            Item studyCountry = this.getItemFromItemMap(study, this.studyCountries, "countryName", this.currentCountry.getName());
            if (studyCountry != null) {
                studyCountry.setAttributeIfNotNull("status", status);
                // studyCountry.setAttributeIfNotNull("plannedEnrolment", plannedEnrolment);
                
                // "dateEnrolment" is "Date of Competent Authority Decision" in EUCTR
                // if (cadDate != null) {
                //     studyCountry.setAttributeIfNotNull("compAuthorityDecisionDate", cadDate.toString());
                // }
                
                // "Date of Ethics Committee Opinion" in EUCTR
                // if (ecdDate != null) {
                //     studyCountry.setAttributeIfNotNull("ethicsCommitteeDecisionDate", ecdDate.toString());
                // }
            } else {
                this.writeLog("Couldn't find StudyCountry with this current country (creating it): \"" + this.currentCountry);
                this.createAndStoreStudyCountry(study, this.currentCountry.getName(), status, null, null, null);
                // this.createAndStoreStudyCountry(study, this.currentCountry.getName(), status, plannedEnrolment, cadDate, null);
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param icStr
     * @param ecStr
     */
    public void parseIEC(Item study, String icStr, String ecStr) {
        if (!this.existingStudy()) {
            /* IEC */
            StringBuilder iec = new StringBuilder();
            
            /* Inclusion criteria */
            if (!ConverterUtils.isNullOrEmptyOrBlank(icStr)) {
                iec.append(icStr);
            }

            /* Exclusion criteria */
            if (!ConverterUtils.isNullOrEmptyOrBlank(ecStr)) {
                if (!iec.toString().isEmpty()) {
                    if (!iec.toString().endsWith(".")) {
                        iec.append(".");
                    }
                    iec.append(" ");
                }
                iec.append(ecStr);
            }


            // Setting IEC string constructed from IC + EC
            String iecStr = iec.toString();
            if (!ConverterUtils.isNullOrEmptyOrBlank(iecStr)) {
                study.setAttributeIfNotNull("iec", iecStr);
            }

            /* Min/max age from IC */
            if (!ConverterUtils.isNullOrEmptyOrBlank(icStr)) {
                // The age criteria is at the end of the IC string
                List<String> ageLines = ConverterUtils.getLastLines(icStr, 6);

                // study.setAttributeIfNotNull("testField5", String.join(";", ageLines));
                
                if (ageLines.size() != 6 || !ageLines.get(0).startsWith("Are the")) {
                    this.writeLog("Malformed IC age end section: " + String.join(";", ageLines));
                } else {
                    // Note: not using number of participants per age range info
                    String under18Line = ageLines.get(0).strip();
                    String under18Value = under18Line.substring(under18Line.lastIndexOf(" ") + 1, under18Line.length());

                    String range1864Line = ageLines.get(2).strip();
                    String range1864Value = range1864Line.substring(range1864Line.lastIndexOf(" ") + 1, range1864Line.length());

                    String over65Line = ageLines.get(4).strip();
                    String over65Value = over65Line.substring(over65Line.lastIndexOf(" ") + 1, over65Line.length());

                    String minAge = "";
                    String maxAge = "";
                    
                    // 18-64 years
                    if (range1864Value.equalsIgnoreCase("yes")) {
                        minAge = "18";
                        maxAge = "64";
                    }
                    
                    // < 18 years
                    if (under18Value.equalsIgnoreCase("yes")) {
                        minAge = "0";   // Used when checking over65Value to only set minAge to 65 if minAge is empty
                        if (maxAge.equals("")) {
                            maxAge = "17";
                        }
                    }

                    // >= 65 years
                    if (over65Value.equalsIgnoreCase("yes")) {
                        maxAge = "";
                        if (minAge.equals("")) {
                            minAge = "65";
                        }
                    }

                    if (minAge.equals("0")) {
                        minAge = "";
                    }

                    if (!ConverterUtils.isNullOrEmptyOrBlank(minAge)) {
                        study.setAttributeIfNotNull("minAge", minAge);
                        study.setAttributeIfNotNull("minAgeUnit", ConverterCVT.AGE_UNIT_YEARS);
                    }

                    if (!ConverterUtils.isNullOrEmptyOrBlank(maxAge)) {
                        study.setAttributeIfNotNull("maxAge", maxAge);
                        study.setAttributeIfNotNull("maxAgeUnit", ConverterCVT.AGE_UNIT_YEARS);
                    }
                }
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param genderStr
     */
    public void parseGender(Item study, String genderStr) {
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(genderStr)) {
            Matcher mGender = P_GENDER.matcher(genderStr);
            if (mGender.matches()) {
                String f = mGender.group(1);
                String m = mGender.group(2);
                if (f.equalsIgnoreCase("yes")) {
                    if (m.equalsIgnoreCase("yes")) {
                        study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_ALL);
                    } else {
                        study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_WOMEN);
                    }
                } else if (m.equalsIgnoreCase("yes")) {
                    study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_MEN);
                } else {
                    this.writeLog("Matched gender string but neither value is yes : " + genderStr);
                }
            } else {
                this.writeLog("Failed to match gender string: " + genderStr);
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param outcomes
     * @param outcomesType
     */
    public void parseOutcomes(Item study, List<String> outcomes, String outcomesType) {
        if (!this.existingStudy() && outcomes.size() > 0) {
            StringBuilder outcomesSb = new StringBuilder();

            // Building outcome string from all the outcome fields
            for (String outcome: outcomes) {
                outcome = outcome.strip();
                outcomesSb.append(outcome);
                if (!outcome.endsWith(".")) {
                    outcomesSb.append(".");
                }
                outcomesSb.append(" ");
            }

            // Setting primary or secondary outcomes
            if (outcomesType.equals("primary")) {
                study.setAttributeIfNotNull("primaryOutcome", outcomesSb.toString().strip());
            } else if (outcomesType.equals("secondary")) {
                study.setAttributeIfNotNull("secondaryOutcomes", outcomesSb.toString().strip());
            } else {
                new Exception("Unknown outcomesType arg value \"" + outcomesType + "\" in parseOutcomes()");
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param secondarySponsors
     */
    public void parseSecondarySponsors(Item study, List<String> secondarySponsors) throws Exception {
        if (!this.existingStudy()) {
            for (String secondarySponsor: secondarySponsors) {
                // TODO: link to CV
                this.createAndStoreClassItem(study, "Organisation",
                        new String[][]{{"contribType", ConverterCVT.CONTRIBUTOR_TYPE_SPONSOR}, {"name", secondarySponsor}});
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param secondaryIDs
     * @throws Exception
     */
    public void parseSecondaryIDs(Item study, List<EuctrSecondaryId> secondaryIDs) throws Exception {
        if (!this.existingStudy() && secondaryIDs.size() > 0) {
            HashSet<String> seenIds = new HashSet<String>();
            String secId;
            String issAuth;
            Matcher mNA;

            // TODO: protocol id for object identifier
            for (EuctrSecondaryId secIdObj: secondaryIDs) {
                secId = secIdObj.getSecondaryId();

                mNA = P_ID_NA.matcher(secId);   // Filtering out N/A and similar values from IDs
                if (!mNA.matches() && !seenIds.contains(secId) && !dummyIDs.contains(secId)) {  // Also filtering out seen and "dummy" IDs
                    issAuth = secIdObj.getIssuingAuthority();

                    // Creating protocol DO if ID is of type "Sponsor Protocol Code"
                    if (issAuth.equalsIgnoreCase(ISS_AUTH_PROTOCOL_CODE)) {
                        this.createAndStoreProtocolDO(study, secId);
                    } else {
                        mNA = P_ID_NA.matcher(issAuth); // Not setting identifier type if issuing authority is N/A (or similar value)
                        // TODO: normalise identifier type
                        
                        // TODO: uncomment once multiple IDs errors resolved
                        // this.createAndStoreClassItem(study, "StudyIdentifier", 
                        //     new String[][]{{"identifierValue", secId}, {"identifierType", mNA.matches() ? "" : issAuth}});
                    }

                    seenIds.add(secId);
                }
            }
        }
    }

    /**
     * TODO
     * Note: The sponsor's protocol code can be modified at any time so we can't set a date/publication year from the fields in the data file
     * https://www.bfarm.de/SharedDocs/Downloads/DE/Arzneimittel/KlinischePruefung/EudraCT_EU-CTR_QuA.pdf?__blob=publicationFile
     * @param study
     * @param protocolCode
     * @throws Exception
     */
    public void createAndStoreProtocolDO(Item study, String protocolCode) throws Exception {
        if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(protocolCode)) {
            // Display title
            String studyDisplayTitle = ConverterUtils.getValueOfItemAttribute(study, "displayTitle");
            String doDisplayTitle;
            if (!ConverterUtils.isNullOrEmptyOrBlank(studyDisplayTitle)) {
                doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TYPE_STUDY_PROTOCOL;
            } else {
                doDisplayTitle = ConverterCVT.O_TYPE_STUDY_PROTOCOL;
            }

            /* Protocol DO */
            Item protocolDO = this.createAndStoreClassItem(study, "DataObject", 
                new String[][]{{"objectClass", ConverterCVT.O_CLASS_TEXT}, {"type", ConverterCVT.O_TYPE_STUDY_PROTOCOL},
                                {"title", doDisplayTitle}});
            
            /* Protocol code ObjectIdentifier */
            this.createAndStoreClassItem(protocolDO, "ObjectIdentifier", 
                new String[][]{{"identifierValue", protocolCode}, {"identifierType", ConverterCVT.ID_TYPE_SPONSOR}});
        }
    }

    /**
     * TODO
     * @param study
     * @param sourceSupports
     * @throws Exception
     */
    public void parseSourceSupports(Item study, List<String> sourceSupports) throws Exception {
        if (!this.existingStudy()) {
            for (String org: sourceSupports) {
                // TODO: match with CV
                if (!ConverterUtils.isNullOrEmptyOrBlank(org)) {
                    this.createAndStoreClassItem(study, "Organisation",
                        new String[][]{{"contribType", ConverterCVT.CONTRIBUTOR_TYPE_STUDY_FUNDER}, {"name", org}});
                }
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param ethicsReviews
     */
    public void parseEthicsReviews(Item study, List<EuctrEthicsReview> ethicsReviews) {
        if (this.currentCountry != null) {
            Item studyCountry = this.getItemFromItemMap(study, this.studyCountries, "countryName", this.currentCountry.getName());

            if (studyCountry != null) {
                for (EuctrEthicsReview er: ethicsReviews) {
                    String erStatus = er.getStatus();
                    
                    if (!ConverterUtils.isNullOrEmptyOrBlank(erStatus)) {
                        studyCountry.setAttributeIfNotNull("status", erStatus);
                    }
                    
                    String erApprovalDateStr = er.getApprovalDate();
                    if (!ConverterUtils.isNullOrEmptyOrBlank(erApprovalDateStr)) {
                        LocalDate erApprovalDate = ConverterUtils.getDateFromString(erApprovalDateStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
                        if (erApprovalDate != null) {
                            studyCountry.setAttributeIfNotNull("ethicsCommitteeDecisionDate", erApprovalDate.toString());
                        } else {
                            this.writeLog("Failed to parse ethics review approval date: " + erApprovalDateStr);
                        }
                    }
                }
            } else {
                this.writeLog("Failed to retrieve StudyCountry in parseEthicsReviews() from country name " + this.currentCountry.getName());
            }
        }
    }

    /**
     * TODO
     * @param euctrObj
     * @param fieldName
     * @return
     * @throws Exception
     */
    public String getAndCleanValue(Object euctrObj, String fieldName) throws Exception {
        Method method = euctrObj.getClass().getMethod("get" + ConverterUtils.capitaliseFirstLetter(fieldName), (Class<?>[]) null);
        String value = (String)method.invoke(euctrObj);
        if (value != null) {
            return this.cleanValue(value, true);
        }
        return value;
    }

    public String cleanValue(String s, boolean strip) {
        if (strip) {
            return s.strip();
        }
        return s;
    }
}