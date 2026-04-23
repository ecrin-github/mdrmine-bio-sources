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

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

import java.io.Reader;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse values from a CTIS data file and store them as MDRMine items
 * Explanation for the fields in the data file:
 * https://www.ema.europa.eu/en/documents/other/clinical-trial-information-system-ctis-public-portal-full-trial-information_en.pdf
 * 
 * @author
 */
public class CtisConverter extends CacheConverter {
    private static final Pattern P_STATUS_COUNTRY = Pattern.compile("^(.+),(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_NOT_APPLICABLE = Pattern.compile("N/A", Pattern.CASE_INSENSITIVE); // N/A
    private static final Pattern P_AGE_SECONDARY_SINGLE = Pattern
            .compile("^[^\\d]*?(<?\\d+\\+?)(?:\\h*-\\h*)?(\\d+)?\\h+(\\w+)[^\\d]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_AGE_SECONDARY_MULTIPLE = Pattern
            .compile("^[^\\d]*?(<?\\d+)[^a-zA-Z]+(\\w+).*?(\\d+\\+?)\\h+(\\w+)[^\\d]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_AGE_PRIMARY = Pattern.compile("^\\h*(\\d+\\+?|[^\\d]+)(?:-?(\\d+))?\\h*(\\w+)?\\h*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_STUDY_INTERVENTIONS = Pattern.compile(
            "^(?:\\[\\\")?(?:not\\h*possible\\h*to\\h*specify|([^\\[]+)\\h+\\[([^\\]]+)]\\h*-\\h*([^\\[]+)\\[([^\\]]+)])(?:\\\"])?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PHASES = Pattern
            .compile("^.*?phase\\h*(iv|iii|ii|i).*?(?:phase\\h*(iv|iii|ii|i).*)?$", Pattern.CASE_INSENSITIVE);

    private static final String AGE_GROUP_IN_UTERO = "In utero";
    private static final String AGE_GROUP_CHILD = "0-17 years";
    private static final String AGE_GROUP_ADULT = "18-64 years";
    private static final String AGE_GROUP_OLDER_ADULT = "65+ years";

    private static final String REGISTRY_ENTRY_BASE_URL = "https://euclinicaltrials.eu/ctis-public/view/";

    private static final String DATASET_TITLE = "CTIS_trials_20241202";
    private static final String DATA_SOURCE_NAME = "CTIS";
    private static final String DATA_SOURCE_DESC = "Clinical Trials Information System (EMA)";

    private Map<String, Integer> fieldsToInd;
    private Map<String, Integer> baseIdsResubmissions = new HashMap<String, Integer>(); // Base ID -> resubmissiong
                                                                                        // number

    /**
     * Constructor
     * 
     * @param writer the ItemWriter used to handle the resultant items
     * @param model  the Model
     */
    public CtisConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * TODO
     * 
     * @param reader
     * @throws Exception
     */
    public void parseData(Reader reader) throws Exception {
        final CSVParser parser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(parser)
                .build();

        boolean skipNext = false;

        this.fieldsToInd = this.getHeaders(csvReader.readNext());

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
                this.writeLog("Failed to parse line");
                nextLine = new String[0];
                skipNext = true;
            }
        }

        csvReader.close();
    }

    /**
     * Parse and store values as MDRMine items and attributes, from a list of values
     * of a line of the data file.
     * 
     * @param lineValues the list of raw values of a line in the data file
     */
    public void parseAndStoreValues(String[] lineValues) throws Exception {
        Item study = createItem("Study");
        // TODO: source id?
        // TODO: SOs publication year

        /* Trial ID */
        String trialID = this.getAndCleanValue(lineValues, "Trial number");

        // Not parsing if existing study is found and with a more recent resubmission
        // number than the current
        if (this.parseTrialID(study, trialID)) {
            /* Study title (need to get it before protocol SO) */
            String trialTitle = this.getAndCleanValue(lineValues, "Title of the trial");
            if (!ConverterUtils.isBlankOrNull(trialTitle)) {
                study.setAttributeIfNotNull("displayTitle", trialTitle);
                this.createAndStoreClassItem(study, "Title",
                        new String[][] { { "text", trialTitle }, { "type", ConverterCVT.TITLE_TYPE_SCIENTIFIC } });
            } else {
                study.setAttributeIfNotNull("displayTitle", ConverterCVT.TITLE_UNKNOWN);
            }

            // Sponsor protocol code
            String protocolCode = this.getAndCleanValue(lineValues, "Protocol code");
            /* Protocol SO */
            this.parseProtocolCode(study, protocolCode);

            /* Trial status */
            String overallTrialStatus = this.getAndCleanValue(lineValues, "Overall trial status");
            this.parseStudyStatus(study, overallTrialStatus);

            /* Study countries (and their status) */
            String locationAndRecruitmentStatus = this.getAndCleanValue(lineValues,
                    "Location(s) and recruitment status");
            this.parseStudyCountries(study, locationAndRecruitmentStatus);

            /* Min/max age */
            String ageGroup = this.getAndCleanValue(lineValues, "Age group");
            String ageRangeSecondaryIdentifier = this.getAndCleanValue(lineValues, "Age range secondary identifier");
            this.parseAgeRanges(study, ageGroup, ageRangeSecondaryIdentifier);

            /* Gender */
            String gender = this.getAndCleanValue(lineValues, "Gender");
            this.parseGender(study, gender);

            /* Planned enrolment */
            String enrolment = this.getAndCleanValue(lineValues, "Number of participants enrolled");
            this.setPlannedEnrolment(study, enrolment);

            /* Trial region */
            // Unused, this value only says if the region of the trial is in the EEA only,
            // or both in EEA and non-EEA countries (or N/A)
            String trialRegion = this.getAndCleanValue(lineValues, "Trial region");

            /* Study conditions */
            // TODO: match with MedDRA terminology
            String medicalConditions = this.getAndCleanValue(lineValues, "Medical conditions");
            this.parseStudyConditions(study, medicalConditions);

            /* Unused: not in our model */
            String therapeuticArea = this.getAndCleanValue(lineValues, "Therapeutic area");
            // this.parseTherapeuticArea(study, therapeuticArea);

            /* Study phase */
            String trialPhase = this.getAndCleanValue(lineValues, "Trial phase");
            this.parseTrialPhase(study, trialPhase);

            /* Study intervention: product */
            String product = this.getAndCleanValue(lineValues, "Product");
            this.parseProduct(study, product);

            /* Primary outcome */
            String primaryEndpoint = this.getAndCleanValue(lineValues, "Primary endpoint");
            this.parsePrimaryEndpoint(study, primaryEndpoint);

            /* Secondary outcome */
            String secondaryEndpoints = this.getAndCleanValue(lineValues, "Secondary endpoints");
            this.parseSecondaryEndpoints(study, secondaryEndpoints);

            // All dates are dd/mm/yyyy

            /* Ethics approval notification SO + decision date */
            String decisionDate = this.getAndCleanValue(lineValues, "Decision date");
            this.parseDecisionDate(study, decisionDate);

            /* Study start date */
            String startDate = this.getAndCleanValue(lineValues, "Start date");
            this.parseStudyStartDate(study, startDate);

            /* Study end date */
            // End date seems like it is the last end date for countries involved in the
            // trial, other is "official" global end
            // Example:
            // https://euclinicaltrials.eu/ctis-public/view/2022-503108-26-00?lang=en
            String endDate = this.getAndCleanValue(lineValues, "End date");
            String globalEndOfTrial = this.getAndCleanValue(lineValues, "Global end of the trial");
            this.parseTrialEndDate(study, endDate, globalEndOfTrial);

            /* Study hasResults */
            String trialResults = this.getAndCleanValue(lineValues, "Trial results");
            this.parseTrialResults(study, trialResults);

            /* Study organisation: sponsors */
            String sponsors = this.getAndCleanValue(lineValues, "Sponsor/Co-Sponsors");
            String sponsorType = this.getAndCleanValue(lineValues, "Sponsor type");
            this.parseSponsors(study, sponsors, sponsorType);

            /* Trial registry entry SO + instance + last updated date */
            String lastUpdatedStr = this.getAndCleanValue(lineValues, "Last updated");
            LocalDate lastUpdated = this.parseDate(lastUpdatedStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
            this.createAndStoreRegistryEntryDO(study, lastUpdated);

            /* Description (constructed) */
            // TODO: missing Main Objective field from CTIS UI
            ConverterUtils.addToDescription(study, product);
            ConverterUtils.addToDescription(study, primaryEndpoint);

            // Storing in cache
            if (!this.existingStudy()) {
                this.studies.put(this.currentTrialID, study);
            }

            this.currentTrialID = null;
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param trialID
     * @return
     */
    public boolean parseTrialID(Item study, String trialID) throws Exception {
        boolean continueParsing = false;

        if (trialID.length() == 17) {
            // Removing resubmission suffix
            this.currentTrialID = trialID.substring(0, 14);
            study.setAttributeIfNotNull("primaryIdentifier", this.currentTrialID);

            try {
                // Checking if the study already exists (resubmission)
                // TODO: store resubmission info somewhere?
                int resubmission = Integer.parseInt(trialID.substring(15, 17));
                if (this.baseIdsResubmissions.containsKey(this.currentTrialID)) { // Existing trial
                    int storedResubmission = this.baseIdsResubmissions.get(this.currentTrialID);
                    if (resubmission > storedResubmission) { // More recent submission number
                        continueParsing = true;

                        // Removing previously stored study
                        this.removeStudyAndLinkedItems(this.currentTrialID);
                    } else if (resubmission < storedResubmission) {
                        this.writeLog("Skipping existing trial with older resubmission number stored, id: "
                                + trialID + ", stored resubmission number: " + storedResubmission);
                    } else {
                        this.writeLog("Existing trial found but with same resubmission number stored, id: "
                                + trialID + ", stored resubmission number: " + storedResubmission);
                    }
                } else { // New trial
                    continueParsing = true;
                }

                // Storing trial in resubmission map
                if (continueParsing) {
                    this.baseIdsResubmissions.put(this.currentTrialID, resubmission);
                }
            } catch (NumberFormatException e) {
                this.writeLog("Failed to parse trial ID suffix as int: " + trialID);
            }
        } else {
            this.writeLog("Unexpected length for trial ID: " + trialID);
        }

        return continueParsing;
    }

    /**
     * TODO
     * 
     * Note: The sponsor's protocol code can be modified at any time so we can't set
     * a date/publication year from the fields in the data file
     * https://www.bfarm.de/SharedDocs/Downloads/DE/Arzneimittel/KlinischePruefung/EudraCT_EU-CTR_QuA.pdf?__blob=publicationFile
     */
    public void parseProtocolCode(Item study, String protocolCode) throws Exception {
        if (!ConverterUtils.isBlankOrNull(protocolCode)) {
            // TODO: Try to get URL from euclinicaltrials.eu/ctis-public/view/[trial ID]?
            // protocol can be in trial documents tab

            // Display title
            String studyDisplayTitle = ConverterUtils.getAttrValue(study, "displayTitle");
            String doDisplayTitle;
            if (!ConverterUtils.isBlankOrNull(studyDisplayTitle)) {
                doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TYPE_PROT;
            } else {
                doDisplayTitle = ConverterCVT.O_TYPE_PROT;
            }

            /* Protocol SO */
            Item protocolDO = this.createAndStoreClassItem(study, "StudyObject",
                    new String[][] { { "objectId", protocolCode },
                            { "primaryIdentifierType", ConverterCVT.ID_TYPE_SPONSOR },
                            { "type", ConverterCVT.O_TYPE_PROT },
                            { "displayTitle", doDisplayTitle } });
        }
    }

    /**
     * TODO
     */
    public void parseStudyStatus(Item study, String overallTrialStatus) {
        study.setAttributeIfNotNull("status", ConverterUtils.normaliseStatus(overallTrialStatus));
    }

    /**
     * TODO
     */
    public void parseStudyCountries(Item study, String locationAndRecruitmentStatus) throws Exception {
        // TODO: normalise values
        if (!ConverterUtils.isBlankOrNull(locationAndRecruitmentStatus)) {
            String[] colonSeparatedValues = locationAndRecruitmentStatus.split(":");

            if (colonSeparatedValues.length > 1) {
                if (colonSeparatedValues.length == 2) {
                    // TODO: check country name not empty?
                    this.createAndStoreClassItem(study, "StudyCountry",
                            new String[][] { { "countryName", colonSeparatedValues[0] },
                                    { "status", colonSeparatedValues[1] } });
                } else {
                    String currentCountry = "";

                    for (int ind = 0; ind < colonSeparatedValues.length; ind++) {
                        if (ind == 0) {
                            currentCountry = colonSeparatedValues[ind];
                        } else if (ind == colonSeparatedValues.length - 1) {
                            this.createAndStoreClassItem(study, "StudyCountry",
                                    new String[][] { { "countryName", currentCountry },
                                            { "status", colonSeparatedValues[ind] } });
                        } else {
                            // Note: this would not work properly if one of the countries names happened to
                            // contain a comma,
                            // which as of writing this never happens in the data
                            Matcher mStatusCountry = P_STATUS_COUNTRY.matcher(colonSeparatedValues[ind]);
                            if (mStatusCountry.matches()) {
                                this.createAndStoreClassItem(study, "StudyCountry",
                                        new String[][] { { "countryName", currentCountry },
                                                { "status", mStatusCountry.group(1) } });
                                currentCountry = mStatusCountry.group(2);
                            } else {
                                this.writeLog("parseStudyCountries(): couldn't match colon separated values, index: "
                                        + ind + ", full string: " + locationAndRecruitmentStatus);
                            }
                        }
                    }
                }
            } else {
                this.writeLog("parseStudyCountries(): couldn't parse \"Location(s) and recruitment status\": "
                        + locationAndRecruitmentStatus);
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param ageGroup
     * @param ageRangeSecondaryIdentifier
     */
    public void parseAgeRanges(Item study, String ageGroup, String ageRangeSecondaryIdentifier) {
        // There are inconsistencies between ageGroup and ageRangeSecondaryIdentifier
        // The ageGroup field is more normalised (same ranges as CTG + "In utero")
        // so we use this one, and fallback to secondary identifier if empty
        // Note: in practice, ageGroup is never null, so ageRangeSecondaryIdentifier is
        // unused
        if (!ConverterUtils.isBlankOrNull(ageGroup)) {
            this.parseAgeGroup(study, ageGroup);
        } else if (!ConverterUtils.isBlankOrNull(ageRangeSecondaryIdentifier)) {
            this.parseAgeRangeSecondaryIdentifier(study, ageRangeSecondaryIdentifier);
            // Fallback to calculating age group if age group field
            study.setAttributeIfNotNull("ageGroup", ConverterUtils.calculateAgeGroup(study));
        }
    }

    /**
     * TODO
     * Unused
     */
    public void parseAgeRangeSecondaryIdentifier(Item study, String ageRangeSecondaryIdentifier) {
        Matcher mAgeSingle = P_AGE_SECONDARY_SINGLE.matcher(ageRangeSecondaryIdentifier);
        String minAge = null;
        String maxAge = null;
        String minAgeUnit = null;
        String maxAgeUnit = null;

        // N/A check
        Matcher mAgeNASecondary = P_NOT_APPLICABLE.matcher(ageRangeSecondaryIdentifier);

        if (!mAgeNASecondary.matches()) {
            if (mAgeSingle.matches()) { // Single range
                String a1 = mAgeSingle.group(1);
                String a2 = mAgeSingle.group(2);
                String u1 = mAgeSingle.group(3);
                if (a2 != null) { // Age range
                    minAge = a1;
                    minAgeUnit = u1;
                    if (a2.endsWith("+")) { // No maximum if 85+
                        maxAge = ConverterCVT.AGE_MAX_YEARS;
                    } else {
                        maxAge = a2;
                        maxAgeUnit = u1;
                    }
                } else { // One number with a < or + sign
                    if (a1.endsWith("+")) { // 85+, no maximum
                        minAge = a1.substring(0, a1.length() - 1);
                        minAgeUnit = u1;
                        maxAge = ConverterCVT.AGE_MAX_YEARS;
                    } else if (!a1.startsWith("<")) { // Ignoring "Gestational age up to <37 weeks, no minimum"
                        this.writeLog(
                                "parseAgeRanges(): only 1 number matched for age but no < or + sign found, string: "
                                        + ageRangeSecondaryIdentifier);
                    }
                }
            } else { // Multiple ranges
                // Note: multiple range Regex assumes that the ranges are sorted from earliest
                // to latest
                Matcher mAgeMultiple = P_AGE_SECONDARY_MULTIPLE.matcher(ageRangeSecondaryIdentifier);
                if (mAgeMultiple.matches()) {
                    String a1 = mAgeMultiple.group(1);
                    String u1 = mAgeMultiple.group(2);
                    String a2 = mAgeMultiple.group(3);
                    String u2 = mAgeMultiple.group(4);
                    if (a1.startsWith("<")) { // Gestational age up to <37 weeks
                        minAge = ConverterCVT.AGE_MIN_YEARS;
                    } else {
                        minAge = a1;
                        minAgeUnit = u1;
                    }
                    if (a2.endsWith("+")) { // 85+
                        maxAge = ConverterCVT.AGE_MAX_YEARS;
                    } else {
                        maxAge = a2;
                        maxAgeUnit = u2;
                    }
                } else {
                    this.writeLog(
                            "parseAgeRanges(): age range string is not empty but couldn't match anything, string: "
                                    + ageRangeSecondaryIdentifier);
                }
            }
        }

        this.setAgesAndUnits(study, minAge, maxAge, minAgeUnit, maxAgeUnit);
    }

    /**
     * TODO
     * ageGroup ranges are not sorted from earliest to latest, so we have to do a
     * different logic than for the secondary field
     * 
     */
    public void parseAgeGroup(Item study, String ageGroup) {
        EnumSet<ConverterCVT.AgeGroup> ageGroups = EnumSet.noneOf(ConverterCVT.AgeGroup.class);

        Integer minAge = null;
        String minAgeUnit = ConverterCVT.AGE_UNIT_YEARS;
        Integer maxAge = null;
        String maxAgeUnit = ConverterCVT.AGE_UNIT_YEARS;

        if (!ConverterUtils.isBlankOrNull(ageGroup)) {
            minAgeUnit = ConverterCVT.AGE_UNIT_YEARS;
            maxAgeUnit = ConverterCVT.AGE_UNIT_YEARS;

            // TODO: ageGroup order
            String[] ranges = ageGroup.split(", ");
            for (String range : ranges) {
                switch (range) {
                    case AGE_GROUP_CHILD:
                        minAge = Integer.valueOf(ConverterCVT.AGE_MIN_YEARS);
                        if (maxAge == null || 17 > maxAge) {
                            maxAge = 17;
                        }

                        ageGroups.add(ConverterCVT.AgeGroup.Pediatric);
                        break;
                    case AGE_GROUP_ADULT:
                        if (minAge == null || 18 < minAge) {
                            minAge = 18;
                        }
                        if (maxAge == null || 64 > maxAge) {
                            maxAge = 64;
                        }

                        ageGroups.add(ConverterCVT.AgeGroup.Adult);
                        break;
                    case AGE_GROUP_OLDER_ADULT:
                        if (minAge == null || 65 < minAge) {
                            minAge = 65;
                        }
                        maxAge = Integer.valueOf(ConverterCVT.AGE_MAX_YEARS);

                        ageGroups.add(ConverterCVT.AgeGroup.OlderAdult);
                        break;
                    case AGE_GROUP_IN_UTERO:
                        minAge = Integer.valueOf(ConverterCVT.AGE_MIN_YEARS); // TODO
                        maxAge = Integer.valueOf(ConverterCVT.AGE_MIN_YEARS);

                        ageGroups.add(ConverterCVT.AgeGroup.InUtero);
                        break;
                    default:
                        this.writeLog("Unknown age range: " + range);
                        break;
                }
            }
        }

        String minAgeStr = null;
        String maxAgeStr = null;

        if (minAge != null) {
            minAgeStr = String.valueOf(minAge);
        }
        if (maxAge != null) {
            maxAgeStr = String.valueOf(maxAge);
        }

        // Age group
        if (ageGroups.size() > 0) {
            study.setAttributeIfNotNull("ageGroup", ConverterUtils.getAgeGroupStr(ageGroups));
        }

        // Min/max age + units
        this.setAgesAndUnits(study, minAgeStr, maxAgeStr, minAgeUnit, maxAgeUnit);
    }

    /**
     * TODO
     * Setting age fields if not empty and study does not already have values for
     * the fields
     */
    public void setAgesAndUnits(Item study, String minAge, String maxAge, String minAgeUnit, String maxAgeUnit) {
        // Min age
        if (!ConverterUtils.isBlankOrNull(minAge) && !ConverterUtils.isBlankOrNull(minAgeUnit)
                && ConverterUtils.isBlankOrNull(ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MIN_AGE))) {
            study.setAttribute("minAge", minAge);
            study.setAttributeIfNotNull("minAgeUnit", minAgeUnit);
        }

        // Max age
        if (!ConverterUtils.isBlankOrNull(maxAge) && !ConverterUtils.isBlankOrNull(maxAgeUnit)
                && ConverterUtils.isBlankOrNull(ConverterUtils.getAttrValue(study, ConverterCVT.FIELD_MAX_AGE))) {
            study.setAttribute("maxAge", maxAge);
            study.setAttributeIfNotNull("maxAgeUnit", maxAgeUnit);
        }
    }

    /**
     * TODO
     */
    public void parseGender(Item study, String gender) {
        gender = gender.toLowerCase();
        if (!ConverterUtils.isBlankOrNull(gender)) {
            if (gender.contains(ConverterCVT.GENDER_MEN.toLowerCase())) {
                if (gender.contains(ConverterCVT.GENDER_WOMEN.toLowerCase())) {
                    study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_ALL);
                } else {
                    study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_MEN);
                }
            } else if (gender.contains(ConverterCVT.GENDER_WOMEN.toLowerCase())) {
                study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_WOMEN);
            } else {
                this.writeLog("parseGender(): value not empty but contains neither \"female\" nor \"male\"");
            }
        }
    }

    /**
     * TODO
     */
    public void setPlannedEnrolment(Item study, String enrolment) {
        if (enrolment != null && !(Long.valueOf(enrolment) > Integer.MAX_VALUE)) {
            study.setAttributeIfNotNull("plannedEnrolment", enrolment);
        }
    }

    /**
     * TODO
     */
    public void parseStudyConditions(Item study, String studyConditions) throws Exception {
        // TODO: separate multiple conditions somehow?
        // TODO: match with MedDRA/other medical terms
        if (!ConverterUtils.isBlankOrNull(studyConditions)) {
            this.linkStudyToStudyCondition(study, studyConditions, null, null, null);
        }
    }

    /**
     * TODO
     * Unused
     */
    public void parseTherapeuticArea(Item study, String studyInterventions) throws Exception {
        HashSet<String> addedCodes = new HashSet<String>();

        String[] separatedInterventions = studyInterventions.split("\",\"");
        for (String interventionPair : separatedInterventions) {
            Matcher mStudyInterventions = P_STUDY_INTERVENTIONS.matcher(interventionPair);
            if (mStudyInterventions.matches()) {
                String t1 = mStudyInterventions.group(1);
                String c1 = mStudyInterventions.group(2);
                String t2 = mStudyInterventions.group(3);
                String c2 = mStudyInterventions.group(4);
                if (t1 != null) {
                    if (!addedCodes.contains(c1)) {
                        // TODO: matching with mesh code and value
                        // Note: these seem to be MeSH Tree codes but couldn't find the version of the
                        // vocabulary, C13 in dataset is C12.050 in MeSH
                        // TODO: intervention type
                        // TODO: normalisation
                        this.linkStudyToIntervention(study, null, t1, null, c1);
                        addedCodes.add(c1);
                    }
                    if (!addedCodes.contains(c2)) { // In theory only the first (parent) intervention can appear
                                                    // multiple
                                                    // times, but we check for the child intervention anyway
                        // TODO: matching with mesh code and value
                        // TODO: intervention type
                        // TODO: normalisation
                        this.linkStudyToIntervention(study, null, t2, null, c2);
                        addedCodes.add(c2);
                    }
                } // else, "not possible to specify" matched
            } else {
                this.writeLog(
                        "parseTherapeuticArea(): couldn't parse study interventions pair: " + interventionPair);
            }
        }
    }

    /**
     * TODO
     */
    public void parseTrialPhase(Item study, String trialPhase) throws Exception {
        if (!ConverterUtils.isBlankOrNull(trialPhase)) {
            Matcher mPhases = P_PHASES.matcher(trialPhase);
            if (mPhases.matches()) {
                String p1 = mPhases.group(1);
                String p2 = mPhases.group(2);
                if (p2 == null) { // One phase number
                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] { { "type", ConverterCVT.FEATURE_T_PHASE },
                                    { "value", ConverterUtils.convertPhaseNumber(p1) } });
                } else { // Two phase numbers
                    this.createAndStoreClassItem(study, "StudyFeature",
                            new String[][] { { "type", ConverterCVT.FEATURE_T_PHASE },
                                    { "value", ConverterUtils.constructMultiplePhasesString(p1, p2) } });
                }
            } else {
                this.writeLog("parseTrialPhase(): couldn't parse trial phase string: " + trialPhase);
            }
        }
    }

    /**
     * TODO
     */
    public void parseProduct(Item study, String product) throws Exception {
        if (!ConverterUtils.isBlankOrNull(product)) {
            // Setting interventions field as well
            study.setAttribute("interventionsDescription", product);

            Matcher mNA = P_NOT_APPLICABLE.matcher(product);
            // TODO: match with CT
            if (!mNA.matches() && !product.equals("-")) {
                this.linkStudyToIntervention(study, ConverterCVT.INTERVENTION_T_DRUG,
                        product, null, null);
            }
        }
    }

    /**
     * TODO
     */
    public void parsePrimaryEndpoint(Item study, String primaryEndpoint) {
        study.setAttributeIfNotNull("primaryOutcome", primaryEndpoint);
    }

    /**
     * TODO
     */
    public void parseSecondaryEndpoints(Item study, String secondaryEndpoints) {
        study.setAttributeIfNotNull("secondaryOutcomes", secondaryEndpoints);
    }

    /**
     * TODO
     */
    public void parseDecisionDate(Item study, String decisionDateStr) throws Exception {
        String studyStatus = ConverterUtils.getAttrValue(study, "status");

        // Check study status to add ethics approval notification
        // TODO: no info of decision date in model if not authorised
        // TODO: values to CVT?
        if (!ConverterUtils.isBlankOrNull(studyStatus)
                && !studyStatus.toLowerCase().equals("not authorised")
                && !studyStatus.toLowerCase().equals("revoked")) {

            LocalDate decisionDate = ConverterUtils.getDateFromString(decisionDateStr,
                    ConverterUtils.P_DATE_D_M_Y_SLASHES);
            if (decisionDate != null) {
                // Display title
                String studyDisplayTitle = ConverterUtils.getAttrValue(study, "displayTitle");
                String doDisplayTitle;
                if (!ConverterUtils.isBlankOrNull(studyDisplayTitle)) {
                    doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TYPE_ETHICS_APPROVAL_NOTIFICATION;
                } else {
                    doDisplayTitle = ConverterCVT.O_TYPE_ETHICS_APPROVAL_NOTIFICATION;
                }

                /* Ethics approval notification SO */
                Item ethicsApprovalDO = this.createAndStoreClassItem(study, "StudyObject",
                        new String[][] { { "type", ConverterCVT.O_TYPE_ETHICS_APPROVAL_NOTIFICATION },
                                { "datePublished", decisionDate.toString() }, // Note: was ConverterCVT.DATE_TYPE_ISSUED
                                                                              // type before model change
                                { "displayTitle", doDisplayTitle } });
            }
        }
    }

    /**
     * TODO
     */
    public void parseStudyStartDate(Item study, String startDateStr) {
        Matcher mNA = P_NOT_APPLICABLE.matcher(startDateStr);
        if (!mNA.matches()) {
            LocalDate startDate = ConverterUtils.getDateFromString(startDateStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
            if (startDate != null) {
                study.setAttributeIfNotNull("startDate", String.valueOf(startDate.toString()));
            }
        }
    }

    /**
     * TODO
     */
    public void parseSponsors(Item study, String sponsors, String sponsorTypes) throws Exception {
        if (!ConverterUtils.isBlankOrNull(sponsors)) {
            HashSet<String> seenSponsors = new HashSet<String>();
            String sponsor;
            String type;

            String[] separatedSponsors = sponsors.split(", ");
            String[] separatedTypes = sponsorTypes.split(", ");
            if (separatedSponsors.length != separatedTypes.length) {
                this.writeLog("parseSponsors(): numbers of sponsors and sponsorTypes don't match, sponsors: "
                        + separatedSponsors.length + " types: " + separatedTypes.length);
            } else {
                for (int i = 0; i < separatedSponsors.length; i++) {
                    sponsor = separatedSponsors[i];
                    type = separatedTypes[i];
                    if (!seenSponsors.contains(sponsor)) {
                        // TODO: organisationRor
                        this.createAndStoreClassItem(study, "Organisation",
                                new String[][] { { "contribType", ConverterCVT.CONTRIB_TYPE_SPONSOR },
                                        { "name", sponsor }, { "type", type } });
                        seenSponsors.add(sponsor);
                    }
                }
            }
        }
    }

    /**
     * TODO
     */
    public void createAndStoreRegistryEntryDO(Item study, LocalDate lastUpdated) throws Exception {
        String studyDisplayTitle = ConverterUtils.getAttrValue(study, "displayTitle");
        String doDisplayTitle;
        if (!ConverterUtils.isBlankOrNull(studyDisplayTitle)) {
            doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TITLE_REGISTRY_ENTRY;
        } else {
            doDisplayTitle = ConverterCVT.O_TITLE_REGISTRY_ENTRY;
        }

        /* Trial registry entry SO */
        this.createAndStoreClassItem(study, "StudyObject",
                new String[][] { { "type", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY },
                        { "dateUpdated", lastUpdated != null ? lastUpdated.toString() : null },
                        { "accessUrl", REGISTRY_ENTRY_BASE_URL + this.currentTrialID },
                        { "accessType", ConverterCVT.O_ACCESS_TYPE_PUBLIC },
                        { "urlTargetType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT },
                        { "displayTitle", doDisplayTitle } });
    }

    /**
     * TODO
     */
    public void parseTrialEndDate(Item study, String endDateStr, String globalEndDateStr) throws Exception {
        String dateStr = null;
        // Attempting to use global end date first, end date second
        Matcher mNA = P_NOT_APPLICABLE.matcher(globalEndDateStr);
        if (!mNA.matches()) {
            dateStr = globalEndDateStr;
        } else {
            mNA = P_NOT_APPLICABLE.matcher(endDateStr);
            if (!mNA.matches()) {
                dateStr = endDateStr;
            }
        }

        if (dateStr != null) {
            LocalDate endDate = ConverterUtils.getDateFromString(dateStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
            if (endDate != null) {
                study.setAttribute("endDate", endDate.toString());
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param trialResults
     */
    public void parseTrialResults(Item study, String trialResults) {
        if (!ConverterUtils.isBlankOrNull(trialResults)) {
            boolean hasResults = false;
            if (trialResults.equalsIgnoreCase("yes")) {
                hasResults = true;
            }
            study.setAttribute("hasResults", String.valueOf(hasResults));
        }
    }

    /**
     * Get field value from array of values using a field's position-lookup Map,
     * value is also cleaned.
     * 
     * @param lineValues the list of all values for a line in the data file
     * @param field      the name of the field to get the value of
     * @return the cleaned value of the field
     * @see //#cleanValue()
     */
    public String getAndCleanValue(String[] lineValues, String field) {
        // TODO: handle errors
        return this.cleanValue(lineValues[this.fieldsToInd.get(field)], true);
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
     * TODO: also move to parent class (abstract)?
     * 
     * @return map of data file field names and their corresponding column index
     */
    public Map<String, Integer> getHeaders(String[] headersList) throws Exception {
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();

        if (headersList.length > 0) {
            for (int ind = 0; ind < headersList.length; ind++) {
                // Deleting the invisible \FEFF unicode character at the beginning of the header
                // file
                if (ind == 0 && Integer.toHexString(headersList[ind].charAt(0) | 0x10000).substring(1).toLowerCase()
                        .equals("feff")) {
                    headersList[ind] = headersList[ind].substring(1);
                }
                fieldsToInd.put(headersList[ind], ind);
            }
        } else {
            throw new Exception("WHO Headers file is empty");
        }

        return fieldsToInd;
    }
}
