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

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;


/**
 * Class to parse values from a CTIS data file and store them as MDRMine items
 * Explanation for the fields in the data file: https://www.ema.europa.eu/en/documents/other/clinical-trial-information-system-ctis-public-portal-full-trial-information_en.pdf
 * @author
 */
public class CtisConverter extends BaseConverter
{
    private static final Pattern P_STATUS_COUNTRY = Pattern.compile("^(.+),(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_NOT_APPLICABLE = Pattern.compile("N/A", Pattern.CASE_INSENSITIVE);  // N/A
    private static final Pattern P_AGE_SECONDARY_SINGLE = Pattern.compile("^[^\\d]*?(<?\\d+\\+?)(?:\\h*-\\h*)?(\\d+)?\\h+(\\w+)[^\\d]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_AGE_SECONDARY_MULTIPLE = Pattern.compile("^[^\\d]*?(<?\\d+)[^a-zA-Z]+(\\w+).*?(\\d+\\+?)\\h+(\\w+)[^\\d]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_AGE_PRIMARY = Pattern.compile("^\\h*(\\d+\\+?|[^\\d]+)(?:-?(\\d+))?\\h*(\\w+)?\\h*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_STUDY_TOPICS = Pattern.compile(
        "^(?:\\[\\\")?(?:not\\h*possible\\h*to\\h*specify|([^\\[]+)\\h+\\[([^\\]]+)]\\h*-\\h*([^\\[]+)\\[([^\\]]+)])(?:\\\"])?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PHASES = Pattern.compile("^.*?phase\\h*(iv|iii|ii|i).*?(?:phase\\h*(iv|iii|ii|i).*)?$", Pattern.CASE_INSENSITIVE);

    private static final String DATASET_TITLE = "CTIS_trials_20241202";
    private static final String DATA_SOURCE_NAME = "CTIS";

    private Map<String, Integer> fieldsToInd;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public CtisConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * Process CTIS data file by iterating on each line of the data file.
     * Method called by InterMine.
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        this.startLogging("ctis");

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
    public void parseAndStoreValues(String[] lineValues) throws Exception {
        Item study = createItem("Study");
        // TODO: source id?

        /* ID */
        String trialID = this.getAndCleanValue(lineValues, "Trial number");
        this.trialID = trialID;

        this.createAndStoreStudyIdentifier(study, trialID, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, null);

        /* Study title (need to get it before protocol DO) */
        String trialTitle = this.getAndCleanValue(lineValues, "Title of the trial");
        if (!ConverterUtils.isNullOrEmptyOrBlank(trialTitle)) {
            study.setAttribute("displayTitle", trialTitle);
            // Note: guessing these are scientific titles by comparing them to WHO scientific titles, as they often contain
            // an acronym of the title at the end of it, and are notably longer than public titles
            this.createAndStoreTitle(study, trialTitle, ConverterCVT.TITLE_TYPE_SCIENTIFIC);
        } else {
            study.setAttribute("displayTitle", "Unknown study title");
        }
        
        // Sponsor protocol code
        String protocolCode = this.getAndCleanValue(lineValues, "Protocol code");
        /* Protocol DO */
        this.createAndStoreProtocolDO(study, protocolCode);

        /* Trial status */
        String overallTrialStatus = this.getAndCleanValue(lineValues, "Overall trial status");
        this.parseStudyStatus(study, overallTrialStatus);

        /* Study countries (and their status) */
        String locationAndRecruitmentStatus = this.getAndCleanValue(lineValues, "Location(s) and recruitment status");
        this.parseStudyCountries(study, locationAndRecruitmentStatus);

        /* Min/max age */
        String ageGroup = this.getAndCleanValue(lineValues, "Age group");
        // TODO: the multiple ranges may not be continuous but our current model only takes into account min and max ages (no range)
        String ageRangeSecondaryIdentifier = this.getAndCleanValue(lineValues, "Age range secondary identifier");
        this.parseAgeRanges(study, ageGroup, ageRangeSecondaryIdentifier);

        /* Gender */
        String gender = this.getAndCleanValue(lineValues, "Gender");
        this.parseGender(study, gender);

        /* Enrolment */
        String enrolment = this.getAndCleanValue(lineValues, "Number of participants enrolled");
        this.setStudyEnrolment(study, enrolment);

        /* Trial region */
        // Unused, this value only says if the region of the trial is in the EEA only, or both in EEA and non-EEA countries (or N/A)
        String trialRegion = this.getAndCleanValue(lineValues, "Trial region");

        /* Study conditions */
        // TODO: match with MedDRA terminology
        String medicalConditions = this.getAndCleanValue(lineValues, "Medical conditions");
        this.parseStudyConditions(study, medicalConditions);
        if (!ConverterUtils.isNullOrEmptyOrBlank(medicalConditions)) {
            study.setAttribute("testField3", "CTIS_" + medicalConditions);
        }

        /* Study topics */
        String therapeuticArea = this.getAndCleanValue(lineValues, "Therapeutic area");
        this.parseStudyTopics(study, therapeuticArea);

        /* Study phase */
        String trialPhase = this.getAndCleanValue(lineValues, "Trial phase");
        this.parseTrialPhase(study, trialPhase);
        if (!ConverterUtils.isNullOrEmptyOrBlank(trialPhase)) {
            study.setAttribute("testField4", "CTIS_" + trialPhase);
        }

        // TODO: create and store refactoring test
        // TODO: construct briefDescription value (product, primary endpoint, field with study features)
        
        store(study);
    }

    /**
     * TODO
     * 
     * Note: The sponsor's protocol code can be modified at any time so we can't set a date/publication year from the fields in the data file
     * https://www.bfarm.de/SharedDocs/Downloads/DE/Arzneimittel/KlinischePruefung/EudraCT_EU-CTR_QuA.pdf?__blob=publicationFile
     */
    public Item createAndStoreProtocolDO(Item study, String protocolCode) throws Exception {
        Item protocolDO = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(protocolCode)) {
            /* Protocol DO */
            protocolDO = createItem("DataObject");
            protocolDO.setAttribute("objectClass", ConverterCVT.O_CLASS_TEXT);
            protocolDO.setAttribute("objectType", ConverterCVT.O_TYPE_STUDY_PROTOCOL);
            protocolDO.setAttribute("title", ConverterCVT.O_TYPE_STUDY_PROTOCOL);

            // TODO: Try to get URL from euclinicaltrials.eu/ctis-public/view/[trial ID]? protocol can be in trial documents tab

            // Display title
            String studyDisplayTitle = ConverterUtils.getDisplayTitleFromStudy(study);
            if (!ConverterUtils.isNullOrEmptyOrBlank(studyDisplayTitle)) {
                protocolDO.setAttribute("displayTitle", studyDisplayTitle + " - " + ConverterCVT.O_TYPE_STUDY_PROTOCOL);
            } else {
                protocolDO.setAttribute("displayTitle", ConverterCVT.O_TYPE_STUDY_PROTOCOL);
            }

            Item objectIdentifier = createItem("ObjectIdentifier");
            objectIdentifier.setAttribute("identifierValue", protocolCode);
            objectIdentifier.setAttribute("identifierType", ConverterCVT.ID_TYPE_SPONSOR);

            protocolDO.addToCollection("objectIdentifiers", objectIdentifier);
            study.addToCollection("studyObjects", protocolDO);
            protocolDO.setReference("linkedStudy", study);
            objectIdentifier.setReference("dataObject", protocolDO);
            store(protocolDO);
            store(objectIdentifier);
        }
        return protocolDO;
    }

    /**
     * TODO
     */
    public Item createAndStoreTitle(Item study, String studyTitleStr, String titleType) throws Exception {
        Item studyTitle = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(studyTitleStr)) {
            studyTitle = createItem("StudyTitle");
            studyTitle.setAttribute("titleType", titleType);
            studyTitle.setAttribute("titleText", studyTitleStr);
            studyTitle.setReference("study", study);
            store(studyTitle);
            study.addToCollection("studyTitles", studyTitle);
        }
        return studyTitle;
    }

    /**
     * TODO
     */
    public void parseStudyStatus(Item study, String overallTrialStatus) {
        // TODO: normalise values
        if (!ConverterUtils.isNullOrEmptyOrBlank(overallTrialStatus)) {
            study.setAttribute("studyStatus", overallTrialStatus);
        }
    }

    /**
     * TODO
     */
    public void parseStudyCountries(Item study, String locationAndRecruitmentStatus) throws Exception {
        // TODO: normalise values
        if (!ConverterUtils.isNullOrEmptyOrBlank(locationAndRecruitmentStatus)) {
            String[] colonSeparatedValues = locationAndRecruitmentStatus.split(":");
            
            if (colonSeparatedValues.length > 1) {
                if (colonSeparatedValues.length == 2) {
                    this.createAndStoreCountry(study, colonSeparatedValues[0], colonSeparatedValues[1]);
                } else {
                    String currentCountry = "";

                    for (int ind = 0; ind < colonSeparatedValues.length; ind++) {
                        if (ind == 0) {
                            currentCountry = colonSeparatedValues[ind];
                        } else if (ind == colonSeparatedValues.length-1) {
                            this.createAndStoreCountry(study, currentCountry, colonSeparatedValues[ind]);
                        } else {
                            // Note: this would not work properly if one of the countries names happened to contain a comma, 
                            // which as of writing this never happens in the data
                            Matcher mStatusCountry = P_STATUS_COUNTRY.matcher(colonSeparatedValues[ind]);
                            if (mStatusCountry.matches()) {
                                this.createAndStoreCountry(study, currentCountry, mStatusCountry.group(1));
                                currentCountry = mStatusCountry.group(2);
                            } else {
                                this.writeLog("parseStudyCountries(): couldn't match colon separated values, index: " 
                                    + ind + ", full string: " + locationAndRecruitmentStatus);
                            }
                        }
                    }
                }
            } else {
                this.writeLog("parseStudyCountries(): couldn't parse \"Location(s) and recruitment status\": " + locationAndRecruitmentStatus);
            }
        }
    }

    /**
     * TODO
     */
    public Item createAndStoreCountry(Item study, String countryName, String status) throws Exception {
        Item studyCountry = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(countryName)) {
            studyCountry = createItem("StudyCountry");
            studyCountry.setAttribute("countryName", countryName);
            if (!ConverterUtils.isNullOrEmptyOrBlank(status)) {
                studyCountry.setAttribute("status", status);
            }
            studyCountry.setReference("study", study);
            store(studyCountry);
            study.addToCollection("studyCountries", studyCountry);
        }
        return studyCountry;
    }

    /**
     * TODO
     * Note: using secondary identifier first and trying to parse ageGroup if N/A
     */
    public void parseAgeRanges(Item study, String ageGroup, String ageRangeSecondaryIdentifier) {
        // TODO: unknown on empty values?
        // TODO: harmonise N/A / None values and cases with WHO parser
        boolean notEmpty = false;   // Used for setting value if no string couldn't be used but at least one of the string is N/A
        boolean alreadyParsed = false;
        if (!ConverterUtils.isNullOrEmptyOrBlank(ageRangeSecondaryIdentifier)) {
            study.setAttribute("testField2", "CTIS_" + ageRangeSecondaryIdentifier);
            notEmpty = true;
            Matcher mAgeNASecondary = P_NOT_APPLICABLE.matcher(ageRangeSecondaryIdentifier);
            if (!mAgeNASecondary.matches()) {
                alreadyParsed = true;
                this.parseAgeRangeSecondaryIdentifier(study, ageRangeSecondaryIdentifier);
            }
        }
        if (!alreadyParsed && !ConverterUtils.isNullOrEmptyOrBlank(ageGroup)) {
            study.setAttribute("testField1", "CTIS_" + ageGroup);
            notEmpty = true;
            Matcher mAgeNAPrimary = P_NOT_APPLICABLE.matcher(ageGroup);
            if (!mAgeNAPrimary.matches()) {
                alreadyParsed = true;
                this.parseAgeGroup(study, ageGroup);
            }
        }
        if (!alreadyParsed && !notEmpty) {  // None of the two values used for parsing but one or both of the values are N/A
            study.setAttribute("minAge", ConverterCVT.NOT_APPLICABLE);
            study.setAttribute("minAgeUnit", ConverterCVT.NOT_APPLICABLE);
            study.setAttribute("maxAge", ConverterCVT.NOT_APPLICABLE);
            study.setAttribute("maxAgeUnit", ConverterCVT.NOT_APPLICABLE);
        }
    }

    /**
     * TODO
     */
    public void parseAgeRangeSecondaryIdentifier(Item study, String ageRangeSecondaryIdentifier) {
        Matcher mAgeSingle = P_AGE_SECONDARY_SINGLE.matcher(ageRangeSecondaryIdentifier);
        String minAge = null;
        String maxAge = null;
        String minAgeUnit = null;
        String maxAgeUnit = null;

        if (mAgeSingle.matches()) { // Single range
            String a1 = mAgeSingle.group(1);
            String a2 = mAgeSingle.group(2);
            String u1 = mAgeSingle.group(3);
            if (a2 != null) {   // Age range
                minAge = a1;
                minAgeUnit = u1;
                if (a2.endsWith("+")) { // No maximum if 85+
                    maxAge = ConverterCVT.NONE;
                } else {
                    maxAge = a2;
                    maxAgeUnit = u1;
                }
            } else {    // One number with a < or + sign
                if (a1.startsWith("<")) {   // Gestational age up to <37 weeks, no minimum
                    minAge = ConverterCVT.NONE;
                    // TODO: this value isn't actually a maximum age, more of a IC
                    maxAge = "Preterm newborn infants (up to gestational age<37 weeks)";
                } else if (a1.endsWith("+")) {  // 85+, no maximum
                    minAge = a1.substring(0, a1.length()-1);
                    minAgeUnit = u1;
                    maxAge = ConverterCVT.NONE;
                } else {
                    this.writeLog("parseAgeRanges(): only 1 number matched for age but no < or + sign found, string: " + ageRangeSecondaryIdentifier);
                }
            }
        } else {    // Multiple ranges
            // Note: multiple range Regex assumes that the ranges are sorted from earliest to latest
            Matcher mAgeMultiple = P_AGE_SECONDARY_MULTIPLE.matcher(ageRangeSecondaryIdentifier);
            if (mAgeMultiple.matches()) {
                String a1 = mAgeMultiple.group(1);
                String u1 = mAgeMultiple.group(2);
                String a2 = mAgeMultiple.group(3);
                String u2 = mAgeMultiple.group(4);
                if (a1.startsWith("<")) {  // Gestational age up to <37 weeks
                    minAge = ConverterCVT.NONE;
                } else {
                    minAge = a1;
                    minAgeUnit = u1;
                }
                if (a2.endsWith("+")) {    // 85+
                    maxAge = ConverterCVT.NONE;
                } else {
                    maxAge = a2;
                    maxAgeUnit = u2;
                }
            } else {
                this.writeLog("parseAgeRanges(): age range string is not empty but couldn't match anything, string: " + ageRangeSecondaryIdentifier);
            }
        }

        this.setAgesAndUnits(study, minAge, maxAge, minAgeUnit, maxAgeUnit);
    }

    /**
     * TODO
     * ageGroup ranges are not sorted from earliest to latest, so we have to do a different logic than for the secondary field
     * 
     */
    public void parseAgeGroup(Item study, String ageGroup) {
        String minAge = null;
        String minAgeUnit = null;
        String maxAge = null;
        String maxAgeUnit = null;

        String[] ranges = ageGroup.split(",");
        for (String range: ranges) {
            Matcher mAgePrimary = P_AGE_PRIMARY.matcher(range);
            if (mAgePrimary.matches()) {
                String a1 = mAgePrimary.group(1);
                String a2 = mAgePrimary.group(2);
                String u = mAgePrimary.group(3);
                if (ConverterUtils.isNullOrEmptyOrBlank(u)) {
                    if (a1.toLowerCase().contains("utero")) {
                        minAge = ConverterCVT.AGE_IN_UTERO;
                    } else {
                        this.writeLog("parseAgeGroup(): matched a string with no number but it is not \"in utero\": " + range);
                    }
                } else {
                    try {
                        if (!ConverterUtils.isNullOrEmptyOrBlank(a2)) {
                            if (maxAge == null || (!maxAge.equals(ConverterCVT.NONE) && Integer.parseInt(a2) > Integer.parseInt(maxAge))) {
                                maxAge = a2;
                                maxAgeUnit = u;
                            }
                            if (minAge == null || (!minAge.equals(ConverterCVT.AGE_IN_UTERO) && Integer.parseInt(a1) < Integer.parseInt(minAge))) {
                                minAge = a1;
                                minAgeUnit = u;
                            }
                        } else {
                            if (a1.endsWith("+")) {
                                maxAge = ConverterCVT.NONE;
                                if (minAge == null) {   // Since this is the max age in the data, we only check that minAge has not been set yet (otherwise the set value is lower)
                                    minAge = a1.substring(0, a1.length()-1);
                                    minAgeUnit = u;
                                }
                            } else {
                                this.writeLog("parseAgeGroup(): only one number found but it doesn't end with a plus sign: " + a1 + ", range: " + range);
                            }
                        }
                    }
                    catch (NumberFormatException e) {
                        this.writeLog("parseAgeGroup(): NumberFormatException in parsing these values: " + a1 + ", " + maxAge);
                    }

                    // Logging changes in the possible values (new units)
                    if (!u.equalsIgnoreCase("years")) {
                        this.writeLog("ageGroup field: new values, units can be different than years, range:" + range);
                    }
                }
            } else {
                this.writeLog("parseAgeGroup(): couldn't match anything in substring: " + range);
            }
        }
        
        this.setAgesAndUnits(study, minAge, maxAge, minAgeUnit, maxAgeUnit);
    }

    /**
     * TODO
     */
    public void setAgesAndUnits(Item study, String minAge, String maxAge, String minAgeUnit, String maxAgeUnit) {
        if (minAge != null) {
            study.setAttribute("minAge", minAge);
            if (minAgeUnit != null && !minAge.equals(ConverterCVT.AGE_IN_UTERO)) {
                study.setAttribute("minAgeUnit", minAgeUnit);
            } else {
                study.setAttribute("minAgeUnit", ConverterCVT.NOT_APPLICABLE);
            }
        }
        if (maxAge != null) {
            study.setAttribute("maxAge", maxAge);
            if (maxAgeUnit != null && !maxAge.equals(ConverterCVT.NONE)) {
                study.setAttribute("maxAgeUnit", maxAgeUnit);
            } else {
                study.setAttribute("maxAgeUnit", ConverterCVT.NOT_APPLICABLE);
            }
        }
    }

    /**
     * TODO
     */
    public void parseGender(Item study, String gender) {
        gender = gender.toLowerCase();
        if (!ConverterUtils.isNullOrEmptyOrBlank(gender)) {
            if (gender.contains(ConverterCVT.GENDER_MEN.toLowerCase())) {
                if (gender.contains(ConverterCVT.GENDER_WOMEN.toLowerCase())) {
                    study.setAttribute("studyGenderElig", ConverterCVT.GENDER_ALL);
                } else {
                    study.setAttribute("studyGenderElig", ConverterCVT.GENDER_MEN);
                }
            } else if (gender.contains(ConverterCVT.GENDER_WOMEN.toLowerCase())) {
                study.setAttribute("studyGenderElig", ConverterCVT.GENDER_WOMEN);
            } else {
                this.writeLog("parseGender(): value not empty but contains neither \"female\" nor \"male\"");
            }
        }
    }

    /**
     * TODO
     */
    public void setStudyEnrolment(Item study, String enrolment) {
        if (enrolment != null) {
            // We have to check that study enrolment does not exceed Integer max value, Long doesn't seem to be properly supported by Intermine
            if (Long.valueOf(enrolment) > Integer.MAX_VALUE) {
                enrolment = String.valueOf((Integer.MAX_VALUE));
            }
            study.setAttribute("studyEnrolment", enrolment);
        }
    }

    /**
     * TODO
     */
    public void parseStudyConditions(Item study, String studyConditions) throws Exception {
        // TODO: separate multiple conditions somehow?
        // TODO: match with MedDRA/other medical terms
        this.createAndStoreCondition(study, studyConditions, ConverterCVT.CV_MEDDRA, null, null, null);
    }

    /**
     * TODO
     */
    public Item createAndStoreCondition(Item study, String originalValue, String originalCTType, String originalCTCode, String icdCode, String icdName) throws Exception {
        Item condition = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(originalValue)) {
            condition = createItem("StudyCondition");
            condition.setAttribute("originalValue", originalValue);
            
            if (!ConverterUtils.isNullOrEmptyOrBlank(originalCTType)) {
                condition.setAttribute("originalCTType", originalCTType);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(originalCTCode)) {
                condition.setAttribute("originalCTCode", originalCTCode);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(icdCode)) {
                condition.setAttribute("icdCode", icdCode);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(icdName)) {
                condition.setAttribute("icdName", icdName);
            }

            condition.setReference("study", study);
            store(condition);
            study.addToCollection("studyConditions", condition);
        }
        return condition;
    }

    /**
     * TODO
     */
    public void parseStudyTopics(Item study, String studyTopics) throws Exception {
        HashSet addedCodes = new HashSet<String>();

        String[] separatedTopics = studyTopics.split("\",\"");
        for (String topicPair: separatedTopics) {
            Matcher mStudyTopics = P_STUDY_TOPICS.matcher(topicPair);
            if (mStudyTopics.matches()) {
                String t1 = mStudyTopics.group(1);
                String c1 = mStudyTopics.group(2);
                String t2 = mStudyTopics.group(3);
                String c2 = mStudyTopics.group(4);
                if (t1 != null) {
                    if (!addedCodes.contains(c1)) {
                        // TODO: topic type
                        // TODO: matching with mesh code and value
                        // Note: these seem to be MeSH Tree codes but couldn't find the version of the vocabulary, C13 in dataset is C12.050 in MeSH
                        this.createAndStoreStudyTopic(study, null, t1, ConverterCVT.CV_MESH_TREE, c1, null, null);
                        addedCodes.add(c1);
                    }
                    if (!addedCodes.contains(c2)) { // In theory only the first (parent) topic can appear multiple times, but we check for the child topic anyway
                        // TODO: topic type
                        // TODO: matching with mesh code and value
                        this.createAndStoreStudyTopic(study, null, t2, ConverterCVT.CV_MESH_TREE, c2, null, null);
                        addedCodes.add(c2);
                    }
                }   // else, "not possible to specify" matched
            } else {
                this.writeLog("parseStudyTopics(): couldn't parse study topics pair: " + topicPair);
            }
        }
    }

    /**
     * TODO
     */
    public Item createAndStoreStudyTopic(Item study, String topicType, String originalValue, 
        String originalCTType, String originalCTCode, String meshCode, String meshValue) throws Exception {
        Item topic = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(originalValue)) {
            topic = createItem("StudyTopic");
            // TODO: original value or another field?
            topic.setAttribute("originalValue", originalValue);
            
            if (!ConverterUtils.isNullOrEmptyOrBlank(topicType)) {
                topic.setAttribute("topicType", topicType);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(originalCTType)) {
                topic.setAttribute("originalCTType", originalCTType);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(originalCTCode)) {
                topic.setAttribute("originalCTCode", originalCTCode);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(meshCode)) {
                topic.setAttribute("meshCode", meshCode);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(meshValue)) {
                topic.setAttribute("meshValue", meshValue);
            }

            topic.setReference("study", study);
            store(topic);
            study.addToCollection("studyTopics", topic);
        }
        return topic;
    }

    /**
     * TODO
     */
    public void parseTrialPhase(Item study, String trialPhase) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(trialPhase)) {
            Matcher mPhases = P_PHASES.matcher(trialPhase);
            if (mPhases.matches()) {
                String p1 = mPhases.group(1);
                String p2 = mPhases.group(2);
                if (p2 == null) {   // One phase number
                    this.createAndStoreClassItem(study, "StudyFeature", 
                        new String[][]{{"featureType", ConverterCVT.FEATURE_PHASE}, {"featureValue", ConverterUtils.convertPhaseNumber(p1)}});
                    // this.createAndStoreStudyFeature(study, ConverterCVT.FEATURE_PHASE, ConverterUtils.convertPhaseNumber(p1));
                } else {    // Two phase numbers
                    this.createAndStoreClassItem(study, "StudyFeature", 
                        new String[][]{{"featureType", ConverterCVT.FEATURE_PHASE}, {"featureValue", ConverterUtils.constructMultiplePhasesString(p1, p2)}});
                    // this.createAndStoreStudyFeature(study, ConverterCVT.FEATURE_PHASE, ConverterUtils.constructMultiplePhasesString(p1, p2));
                }
            } else {
                this.writeLog("parseTrialPhase(): couldn't parse trial phase string: " + trialPhase);
            }
        }
    }

    /**
     * TODO
     */
    public Item createAndStoreStudyFeature(Item study, String featureType, String featureValue) throws Exception {
        Item feature = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(featureValue)) {
            feature = createItem("StudyFeature");
            feature.setAttribute("featureValue", featureValue);
            if (!ConverterUtils.isNullOrEmptyOrBlank(featureType)) {
                feature.setAttribute("featureType", featureType);
            }
        }
        return feature;
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
        return this.cleanValue(lineValues[this.fieldsToInd.get(field)], true);
    }

    public String cleanValue(String s, boolean strip) {
        if (strip) {
            return s.strip();
        }
        return s;
    }

    /**
     * Get a dictionary (map) of the WHO data file field names linked to their corresponding column index in the data file, using a separate headers file.
     * The headers file path is defined in the project.xml file (and set as an instance attribute of this class).
     * TODO: also move to parent class (abstract)?
     * 
     * @return map of data file field names and their corresponding column index
     */
    public Map<String, Integer> getHeaders(String[] headersList) throws Exception {
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();

        if (headersList.length > 0) {
            for (int ind = 0; ind < headersList.length; ind++) {
                // Deleting the invisible \FEFF unicode character at the beginning of the header file
                if (ind == 0 && Integer.toHexString(headersList[ind].charAt(0) | 0x10000).substring(1).toLowerCase().equals("feff")) {
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
