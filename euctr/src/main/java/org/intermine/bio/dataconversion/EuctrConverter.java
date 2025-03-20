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

import java.io.Reader;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.model.bio.Country;
import org.intermine.xml.full.Item;



/**
 * 
 * @author
 */
public class EuctrConverter extends CacheConverter
{
    private static final String DATASET_TITLE = "EUCTR_allfile";
    private static final String DATA_SOURCE_NAME = "EUCTR";

    private static final Pattern P_TITLE_NA = Pattern.compile("^-|_|N\\/?A$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PHASE = Pattern.compile(
        ".*Phase\\h*i\\):\\h*(no|yes|)?\\n.*\\(Phase\\h*ii\\):\\h*(no|yes|)?\\n.*\\(Phase\\h*iii\\):\\h*(no|yes|)?\\n.*\\(Phase\\h*iv\\):\\h*(no|yes|)?.*", 
        Pattern.CASE_INSENSITIVE);

    private static final String FEATURE_YES = "yes";
    private static final String FEATURE_NO = "no";

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
            if (mainId.length() != 17) {
                this.writeLog("Unexpected length for study id: " + mainId);
            } else {
                // ID without country code suffix
                String cleanedID = mainId.substring(0, 14);
                String countryCode = mainId.substring(15, 17);
                
                if (this.studies.containsKey(cleanedID)) {   // Adding country-specific info to existing trial
                    this.existingStudy = this.studies.get(cleanedID);
                    study = this.existingStudy;
                    this.writeLog("Add country info, trial ID: " + cleanedID);
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
    
                /* EUCTR trial ID */
                String trialUrl = this.getAndCleanValue(mainInfo, "url");
                // TODO: also add ID with suffix?
                if (!this.existingStudy()) {
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
                if (!this.existingStudy()) {
                    this.createAndStoreStudyIdentifier(study, trialUtrn, null, null);
                }
    
                // Unused, name of registry, seems to always be EUCTR
                String regName = this.getAndCleanValue(mainInfo, "regName");
    
                // "Date on which this record was first entered in the EudraCT database" (from trials-full.txt dat format)
                // Using this as "newer update" date
                String dateRegistrationStr = this.getAndCleanValue(mainInfo, "dateRegistration");
                LocalDate dateRegistration = this.parseDate(dateRegistrationStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
    
                /* Trial registry entry DO */
                this.createAndStoreRegistryEntryDO(study, dateRegistration, trialUrl);
    
                /* Study people: primary sponsor */
                String primarySponsor = this.getAndCleanValue(mainInfo, "primarySponsor");
                this.parsePrimarySponsor(study, primarySponsor);
    
                // Unused, always empty
                String acronym = this.getAndCleanValue(mainInfo, "acronym");
    
                /* Date enrolment (start date) */
                String dateEnrolmentStr = this.getAndCleanValue(mainInfo, "dateEnrolment");
                LocalDate dateEnrolment = this.parseDate(dateEnrolmentStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
                this.setStudyStartDate(study, dateEnrolment);
                if (dateEnrolment != null) {    // (?)
                    study.setAttributeIfNotNull("testField1", "EUCTR_" + (dateEnrolment != null ? dateEnrolment.toString() : null));
                }
    
                // Unused, "Date trial authorised"
                String typeEnrolment = this.getAndCleanValue(mainInfo, "typeEnrolment");
                study.setAttributeIfNotNull("testField2", "EUCTR_" + typeEnrolment);
    
                // For the whole trial
                String targetSize = this.getAndCleanValue(mainInfo, "targetSize");
                this.setPlannedEnrolment(study, targetSize);
    
                // "Not Recruiting" or "Authorised-recruitment may be ongoing or finished" or NA
                String recruitmentStatus = this.getAndCleanValue(mainInfo, "recruitmentStatus");
                if (!this.existingStudy() && !ConverterUtils.isNullOrEmptyOrBlank(recruitmentStatus) && !recruitmentStatus.equalsIgnoreCase("NA")) {
                    study.setAttributeIfNotNull("studyStatus", recruitmentStatus);
                }
                study.setAttributeIfNotNull("testField4", "EUCTR_" + recruitmentStatus);
                
                /* Note: from https://www.clinicaltrialsregister.eu/about.html, we can read: 
                * The EU Clinical Trials Register does not: 
                * provide information on non-interventional clinical trials of medicines (observational studies on authorised medicines);
                * provide information on clinical trials for surgical procedures, medical devices or psychotherapeutic procedures; 
                */
                // This is always "Interventional clinical trial of medicinal product"
                // String studyType = this.getAndCleanValue(mainInfo, "studyType");
                if (!this.existingStudy()) {
                    study.setAttributeIfNotNull("studyType", ConverterCVT.TYPE_INTERVENTIONAL);
                }
                
                /* Study features */
                String studyDesign = this.getAndCleanValue(mainInfo, "studyDesign");
                study.setAttributeIfNotNull("testField5", "EUCTR_" + studyDesign);
                this.parseStudyDesign(study, studyDesign);
                
                /* Study feature: phase */
                String phase = this.getAndCleanValue(mainInfo, "phase");
                study.setAttributeIfNotNull("testField6", "EUCTR_" + phase);
                this.parsePhase(study, phase);
                
                // TODO: health condition freetext, use with hc code and hc keyword fields
                String hcFreetext = this.getAndCleanValue(mainInfo, "hcFreetext");
                
                // TODO: intervention freetext, use with i code and i keyword fields
                String iFreetext = this.getAndCleanValue(mainInfo, "iFreetext");
                
                // TODO: results
                // For the whole trial
                String resultsActualEnrolment = this.getAndCleanValue(mainInfo, "resultsActualEnrolment");
                String resultsDateCompleted = this.getAndCleanValue(mainInfo, "resultsDateCompleted");
                String resultsUrlLink = this.getAndCleanValue(mainInfo, "resultsUrlLink");
                String resultsSummary = this.getAndCleanValue(mainInfo, "resultsSummary");
                String resultsDatePosted = this.getAndCleanValue(mainInfo, "resultsDatePosted");
                String resultsDateFirstPublication = this.getAndCleanValue(mainInfo, "resultsDateFirstPublication");
                String resultsBaselineChar = this.getAndCleanValue(mainInfo, "resultsBaselineChar");
                String resultsParticipantFlow = this.getAndCleanValue(mainInfo, "resultsParticipantFlow");
                String resultsAdverseEvents = this.getAndCleanValue(mainInfo, "resultsAdverseEvents");
                String resultsOutcomeMeasures = this.getAndCleanValue(mainInfo, "resultsOutcomeMeasures");
                String resultsUrlProtocol = this.getAndCleanValue(mainInfo, "resultsUrlProtocol");
                String resultsIPDPlan = this.getAndCleanValue(mainInfo, "resultsIPDPlan");
                String resultsIPDDescription = this.getAndCleanValue(mainInfo, "resultsIPDDescription");

                /*

                List<EuctrContact> contacts = trial.getContacts();
                if (contacts.size() > 0) {
                    this.createAndStoreClassItem(study, "StudyPeople", 
                        new String[][]{{"personFullName", contacts.get(0).getFirstname()}});
                }

                */

                /* Study countries */
                List<String> countries = trial.getCountries();
                this.parseCountries(study, countries);
    
                /*

                EuctrCriteria criteria = trial.getCriteria();
                String ic = criteria.getInclusionCriteria();
                study.setAttributeIfNotNull("iec", ic);
                
                */
    
                if (!this.existingStudy()) {
                    this.studies.put(this.currentTrialID, study);
                }
    
                this.currentTrialID = null;
            }
        }
    }

    /**
     * TODO
     */
    public void parseTitles(Item study, String publicTitle, String scientificTitle, String scientificAcronym) throws Exception {
        if (!this.existingStudy()) {
            boolean displayTitleSet = false;
            Matcher mPublicTitleNA = P_TITLE_NA.matcher(publicTitle);
            if (!ConverterUtils.isNullOrEmptyOrBlank(publicTitle) && !mPublicTitleNA.matches()) {
                study.setAttributeIfNotNull("displayTitle", publicTitle);
                displayTitleSet = true;
                this.createAndStoreClassItem(study, "StudyTitle", 
                    new String[][]{{"titleText", publicTitle}, {"titleType", ConverterCVT.TITLE_TYPE_PUBLIC}});
            }
    
            Matcher mScientificTitleNA = P_TITLE_NA.matcher(scientificTitle);
            if (!ConverterUtils.isNullOrEmptyOrBlank(scientificTitle) && !mScientificTitleNA.matches()) {
                if (!displayTitleSet) {
                    study.setAttributeIfNotNull("displayTitle", scientificTitle);
                    displayTitleSet = true;
                }
                this.createAndStoreClassItem(study, "StudyTitle", 
                    new String[][]{{"titleText", scientificTitle}, {"titleType", ConverterCVT.TITLE_TYPE_SCIENTIFIC}});
            }
    
            if (!displayTitleSet) {
                // TODO: only 1 matcher object?
                Matcher mScientificAcronymNA = P_TITLE_NA.matcher(scientificAcronym);
                if (!mScientificAcronymNA.matches()) {
                    study.setAttributeIfNotNull("displayTitle", scientificAcronym);
                }
            }
        }
    }

    /**
     * TODO
     */
    public void parsePrimarySponsor(Item study, String primarySponsor) throws Exception {
        if (!this.existingStudy()) {
            this.createAndStoreClassItem(study, "StudyOrganisation", 
                new String[][]{{"contribType", ConverterCVT.CONTRIBUTOR_TYPE_SPONSOR}, 
                                {"organisationName", primarySponsor}});
        }
    }

    /**
     * TODO
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
                new String[][]{{"objectType", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY}, {"objectClass", ConverterCVT.O_CLASS_TEXT},
                                {"title", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY}, {"displayTitle", doDisplayTitle}});

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
                Item doRegistryEntry = this.getItemFromItemMap(study, this.studyObjects, "objectType", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY);
                if (doRegistryEntry != null) {
                    Item creationOD = this.getItemFromItemMap(doRegistryEntry, this.objectDates, "dateType", ConverterCVT.DATE_TYPE_CREATED);
                    if (creationOD != null) {
                        String existingDateStr = ConverterUtils.getValueOfItemAttribute(creationOD, "startDate");
                        // Updating creation date if older than known creation date
                        if (!ConverterUtils.isNullOrEmptyOrBlank(existingDateStr)
                            && creationDate.compareTo(ConverterUtils.getDateFromString(existingDateStr, null)) < 0) {
                                creationOD.setAttribute("startDate", creationDate.toString());
                            this.writeLog("older creation date: " + creationDate.toString() + ", previous: " + existingDateStr + " -country: " + this.currentCountry);
                            // Using record registration date as "newer last update"
                            // TODO: use a different variable?
                            this.newerLastUpdate = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * 
     * @param study
     * @param plannedEnrolment
     */
    public void setPlannedEnrolment(Item study, String plannedEnrolment) {
        if (ConverterUtils.isPosWholeNumber(plannedEnrolment) && !(Long.valueOf(plannedEnrolment) > Integer.MAX_VALUE)) {
            study.setAttributeIfNotNull("testField3", "EUCTR_" + plannedEnrolment);
            if (!this.existingStudy() || this.newerLastUpdate) {    // Updating planned enrolment for more recent record registration date
                study.setAttributeIfNotNull("plannedEnrolment", plannedEnrolment);
            }
        }
    }

    /**
     * 
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
                    this.writeLog("Failed to split feature-value tuple with colon: " + tuple);
                } else if (tuple.length == 1) {
                    this.writeLog("Split feature-value tuple with colon but got only 1 value: " + tuple);
                } else if (tuple.length > 2) {
                    this.writeLog("Split feature-value tuple with colon but got more than 2 values: " + tuple);
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

    /*
     * TODO
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

    /*
     * TODO
     */
    public void parseCountries(Item study, List<String> countries) throws Exception {
        // TODO: don't add duplicates (EUCTR2008-007326-19)
        String status = ConverterUtils.getValueOfItemAttribute(study, "studyStatus");

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
     */
    public String getAndCleanValue(Object euctrObj, String fieldName) throws Exception {
        Method method = euctrObj.getClass().getMethod("get" + ConverterUtils.capitaliseFirstLetter(fieldName), (Class<?>[]) null);
        String value = (String)method.invoke(euctrObj);
        if (value != null) {
            return this.cleanValue(value, true);
        }
        return value;
    }

    /**
     * TODO
     */
    public String cleanValue(String s, boolean strip) {
        if (strip) {
            return s.strip();
        }
        return s;
    }

    /**
     * TODO
     */
    public Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item item = this.createClassItem(mainClassItem, className, kv);

        if (item != null) {
            String mapName = this.getReverseReferenceNameOfClass(className);
            Map<String, List<Item>> itemMap = (Map<String, List<Item>>) EuctrConverter.class.getSuperclass().getDeclaredField(mapName).get(this);
            if (itemMap != null) {
                this.saveToItemMap(mainClassItem, itemMap, item);
            } else {
                this.writeLog("Failed to save EUCTR item to map, class name: " + className);
            }
        } else {
            this.writeLog("Failed to create item of class " + className + ", attributes: " + kv);
        }

        return item;
    }
}