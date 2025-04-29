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
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import org.apache.commons.text.WordUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;



/**
 * 
 * @author
 */
public class CtgConverter extends BaseConverter
{
    //
    private static final String DATASET_TITLE = "CTG-Studies";
    private static final String DATA_SOURCE_NAME = "CTG";

    private static final Pattern P_PHASE = Pattern.compile("(NA)|(early_)?phase(\\d)(?:\\|phase(\\d))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DOC = Pattern.compile("(.*?),\\h*(http\\S+)\\h*", Pattern.CASE_INSENSITIVE);

    private static final String AGE_CHILD = "CHILD";
    private static final String AGE_ADULT = "ADULT";
    private static final String AGE_OLDER_ADULT = "OLDER_ADULT";

    private Map<String, Integer> fieldsToInd;
    private HashSet<String> storedPKs = new HashSet<String>();  // Storing all NCT, EUCTR, and CTIS id to avoid duplicate errors

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public CtgConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        this.startLogging("ctg");

        final CSVParser parser = new CSVParserBuilder()
                                        .withSeparator(',')
                                        // .withQuoteChar('"')
                                        .build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                                            .withCSVParser(parser)
                                            .build();

        /* Headers */
        this.fieldsToInd = this.getHeaders(csvReader);

        /* Reading file */
        boolean skipNext = false;

        // nextLine[] is an array of values from the line
        String[] nextLine = csvReader.readNext();

        // TODO: performance tests? compared to iterator
        while (nextLine != null) {
            if (!skipNext) {
                this.parseAndStoreTrial(nextLine);
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

        this.stopLogging();
        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    public void parseAndStoreTrial(String[] lineValues) throws Exception {
        Item study = createItem("Study");

        /* Trial ID */
        String trialID = this.getAndCleanValue(lineValues, "NCT Number");
        /* Secondary IDs (EUCTR, CTIS, Protocol code, etc.) */
        String otherIDs = this.getAndCleanValue(lineValues, "Other IDs");

        // Not storing study if trialID is blank or is the trial that is not parsed properly or is a trial with an ID that is already linked to another study
        // TODO: if studies with blank trial IDs exist, check otherIDs then?
        if (!ConverterUtils.isNullOrEmptyOrBlank(trialID) && !trialID.equals("NCT01027572") &&
            this.parseTrialIDs(study, trialID, otherIDs)) {
            /* Study title */
            String studyTitle = this.getAndCleanValue(lineValues, "Study Title");
            // Study acronym
            String acronym = this.getAndCleanValue(lineValues, "Acronym");
            this.parseStudyTitle(study, studyTitle, acronym);
            
            // TODO
            Item studySource = createItem("StudySource");
            studySource.setAttribute("sourceName", "CTG");
            studySource.setReference("study", study);
            store(studySource);
    
            // Registry trial page URL (used later for registry entry and results summary DO)
            String studyURL = this.getAndCleanValue(lineValues, "Study URL");
            
            /* Study status */
            String studyStatus = this.getAndCleanValue(lineValues, "Study Status");
            this.parseStatus(study, studyStatus);
    
            /* Study brief description */
            String briefSummary = this.getAndCleanValue(lineValues, "Brief Summary");
            this.parseBriefSummary(study, briefSummary);
    
            // TODO: use this to parse results or not?
            String studyResults = this.getAndCleanValue(lineValues, "Study Results");
    
            /* Study conditions */
            String conditions = this.getAndCleanValue(lineValues, "Conditions");
            this.parseConditions(study, conditions);
    
            /* Study topics */
            String interventions = this.getAndCleanValue(lineValues, "Interventions");
            this.parseInterventions(study, interventions);
            study.setAttributeIfNotNull("testField2", interventions);
    
            /* Primary outcomes */
            String primaryOutcomeMeasures = this.getAndCleanValue(lineValues, "Primary Outcome Measures");
            this.parsePrimaryOutcomes(study, primaryOutcomeMeasures);
            
            /* Secondary outcomes */
            String secondaryOutcomeMeasures = this.getAndCleanValue(lineValues, "Secondary Outcome Measures");
            String otherOutcomeMeasures = this.getAndCleanValue(lineValues, "Other Outcome Measures");
            this.parseSecondaryOutcomes(study, secondaryOutcomeMeasures, otherOutcomeMeasures);
    
            /* Trial sponsor */
            String sponsor = this.getAndCleanValue(lineValues, "Sponsor");
            this.parseSponsor(study, sponsor);
            
            /* Trial collaborating organisations */
            String collaborators = this.getAndCleanValue(lineValues, "Collaborators");
            this.parseCollaborators(study, collaborators);

            /* Gender */
            String gender = this.getAndCleanValue(lineValues, "Sex");
            this.parseGender(study, gender);
            
            /* Min/max age */
            String age = this.getAndCleanValue(lineValues, "Age");
            this.parseAge(study, age);

            /* Study feature: phase */
            String phases = this.getAndCleanValue(lineValues, "Phases");
            this.parsePhases(study, phases);

            /* Study planned/actual enrolment */
            String enrolment = this.getAndCleanValue(lineValues, "Enrollment");
            this.parseEnrolment(study, enrolment);

            // Unused (see wiki)
            String funderType = this.getAndCleanValue(lineValues, "Funder Type");
            study.setAttributeIfNotNull("testField3", funderType);
            
            /* Study type */
            String studyType = this.getAndCleanValue(lineValues, "Study Type");
            this.parseStudyType(study, studyType);
            
            /* Study features */
            String studyDesign = this.getAndCleanValue(lineValues, "Study Design");
            this.parseStudyDesign(study, studyDesign);
            
            /* Study start date */
            String startDateStr = CtgConverter.normaliseDateString(this.getAndCleanValue(lineValues, "Start Date"));
            this.parseStartDate(study, startDateStr);
            
            /* Study end date */
            // Last primary outcome measure data collection date
            String primaryCompletionDateStr = CtgConverter.normaliseDateString(this.getAndCleanValue(lineValues, "Primary Completion Date"));
            String completionDateStr = CtgConverter.normaliseDateString(this.getAndCleanValue(lineValues, "Completion Date"));
            // Last data collection (all outcome measures) date
            LocalDate primaryCompletionDate = ConverterUtils.getDateFromString(primaryCompletionDateStr, null);
            LocalDate completionDate = ConverterUtils.getDateFromString(completionDateStr, null);
            this.parseCompletionDates(study, completionDate, primaryCompletionDate);

            // Record posted (available) date
            String firstPostedStr = this.getAndCleanValue(lineValues, "First Posted");
            LocalDate firstPosted = ConverterUtils.getDateFromString(firstPostedStr, null);
            study.setAttributeIfNotNull("testField4", firstPostedStr);
            
            // Record last update date
            String lastUpdatePostedStr = this.getAndCleanValue(lineValues, "Last Update Posted");
            LocalDate lastUpdatePosted = ConverterUtils.getDateFromString(lastUpdatePostedStr, null);
            study.setAttributeIfNotNull("testField5", lastUpdatePostedStr);
            
            // Results posted (available) date
            String resultsFirstPostedStr = this.getAndCleanValue(lineValues, "Results First Posted");
            LocalDate resultsFirstPosted = ConverterUtils.getDateFromString(resultsFirstPostedStr, null);
            study.setAttributeIfNotNull("testField6", resultsFirstPostedStr);

            /* Trial registry entry DO */
            this.createAndStoreRegistryEntryDO(study, studyURL, firstPosted, lastUpdatePosted);

            /* Trial results summary DO */
            this.createAndStoreResultsSummaryDO(study, studyResults, studyURL, completionDate, primaryCompletionDate, resultsFirstPosted, lastUpdatePosted);
            
            /* Study locations */
            String locations = this.getAndCleanValue(lineValues, "Locations");
            this.parseLocations(study, locations);
            study.setAttributeIfNotNull("testField7", locations);
            
            String studyDocuments = this.getAndCleanValue(lineValues, "Study Documents");
            this.parseStudyDocuments(study, studyDocuments);
            study.setAttributeIfNotNull("testField8", studyDocuments);
    
            store(study);

        }
        this.currentTrialID = null;
    }

    /**
     * TODO
     * @param study
     * @param studyTitle
     */
    public void parseTrialID(Item study, String trialID) {
        // NCT ID
        study.setAttributeIfNotNull("nctID", trialID);
    }

    /**
     * TODO
     * @param study
     * @param mainTrialID
     * @param otherIDsStr
     * @return
     * @throws Exception
     */
    public boolean parseTrialIDs(Item study, String mainTrialID, String otherIDsStr) throws Exception {
        boolean continueParsing = true;

        // NCT ID
        if (storedPKs.contains(mainTrialID)) {
            continueParsing = false;
            this.writeLog("NCT ID already exists: " + mainTrialID);
        } else {
            this.currentTrialID = mainTrialID;
            study.setAttributeIfNotNull("nctID", mainTrialID);
        }

        // Other IDs
        if (!ConverterUtils.isNullOrEmptyOrBlank(otherIDsStr) && continueParsing) {
            study.setAttributeIfNotNull("testField1", otherIDsStr);
            boolean ctisIdSet = false;
            boolean euctrIdSet = false;
            List<String> euIds = new ArrayList<String>();
            List<String> otherIds = new ArrayList<String>();    // IDs to be added as "StudyIdentifier" items later

            // Adding secondaryIDs and trialID into one set
            Set<String> ids = Stream.of(otherIDsStr.split("\\|"))
                .map(String::strip)
                .collect(Collectors.toSet());

            Iterator<String> idsIter = ids.iterator();
            while (idsIter.hasNext() && continueParsing) {
                String otherID = idsIter.next();

                Matcher mEu = ConverterUtils.P_EU_ID.matcher(otherID);
                if (mEu.matches()) {
                    String ctisPrefix = mEu.group(1);
                    String euctrPrefix = mEu.group(2);
                    String euId = mEu.group(3);
                    String ctisSuffix = mEu.group(4);
                    String euctrSuffix = mEu.group(5);

                    if (storedPKs.contains(euId)) {
                        continueParsing = false;
                        this.writeLog("EU ID already exists: " + euId + "; raw id: " + otherID);
                    } else {
                        if (ctisPrefix != null || ctisSuffix != null) { // CTIS ID
                            if (euctrPrefix == null && euctrSuffix == null) {
                                // Setting CTIS ID without prefix and suffix
                                study.setAttributeIfNotNull("primaryIdentifier", euId);
                                ctisIdSet = true;
                            } else {
                                this.writeLog("CTIS ID matched but also has EUCTR ID characteristics: " + otherID);
                            }
                        } else if (euctrPrefix != null || euctrSuffix != null) {    // EUCTR ID
                            // Setting EUCTR ID without prefix and suffix
                            study.setAttributeIfNotNull("euctrID", euId);
                            euctrIdSet = true;
                            
                            if (euctrSuffix != null) {
                                this.writeLog("EUCTR ID matched and suffix is not null: " + euctrSuffix);
                            }
                        } else {    // Undistinguishable ID
                            if (ctisIdSet) {
                                if (!euctrIdSet) {
                                    study.setAttributeIfNotNull("euctrID", euId);
                                    euctrIdSet = true;
                                } else {
                                    this.writeLog("Found an EU id but both CTIS and EUCTR ID are already set, id: " + euId + "; full string of IDs: " + otherIDsStr);
                                }
                            } else if (ctisIdSet) {
                                study.setAttributeIfNotNull("primaryIdentifier", euId);
                                ctisIdSet = true;
                            } else {
                                euIds.add(euId);
                            }
                        }
                    }
                } else {
                    otherIds.add(otherID);
                }
            }

            if (continueParsing) {
                // Adding other IDs as StudyIdentifier items
                for (String otherID: otherIds) {
                    // TODO: infer ID type
                    this.createAndStoreClassItem(study, "StudyIdentifier", 
                        new String[][]{{"identifierValue", otherID}});
                }
                
                // Handling undistinguishable EU IDs
                if (euIds.size() > 0) {
                    if (euIds.size() > 2) {
                        this.writeLog("More than 2 EU IDs found: " + euIds + "; full string of IDs: " + otherIDsStr);
                    } else if (euIds.size() == 2) {
                        if (ctisIdSet || euctrIdSet) {
                            this.writeLog("2 EU IDs found but CTIS ID or EUCTR ID has already been set: " + euIds + "; full string of IDs: " + otherIDsStr);
                        } else {
                            String id1 = euIds.get(0);
                            String id2 = euIds.get(1);
    
                            // Assuming that the more recent ID (year + sequential part after) is the CTIS ID, and the other is the EUCTR ID
                            if (id1.compareTo(id2) > 0) {
                                study.setAttributeIfNotNull("primaryIdentifier", id1);
                                study.setAttributeIfNotNull("euctrID", id2);
                            } else {
                                study.setAttributeIfNotNull("primaryIdentifier", id2);
                                study.setAttributeIfNotNull("euctrID", id1);
                            }
                        }
                    } else {    // 1 ID
                        if (ctisIdSet && euctrIdSet) {
                            this.writeLog("1 EU ID found but both CTIS and EUCTR IDs have already been set: " + euIds + "; full string of IDs: " + otherIDsStr);
                        } else {
                            String id1 = euIds.get(0);
    
                            // Note: if both ctisID and euctrID have not been set before, we populate both fields hoping for a merge to correct the fields later
                            if (!ctisIdSet) {
                                study.setAttributeIfNotNull("primaryIdentifier", id1);
                            }
                            if (!euctrIdSet) {
                                study.setAttributeIfNotNull("euctrID", id1);
                            }
                        }
                    }
                }
            }
        }

        if (continueParsing) {
            String nctID = ConverterUtils.getValueOfItemAttribute(study, "nctID");
            String euctrID = ConverterUtils.getValueOfItemAttribute(study, "euctrID");
            String ctisID = ConverterUtils.getValueOfItemAttribute(study, "primaryIdentifier");

            for (String id: new String[]{nctID, ctisID, euctrID}) {
                if (!ConverterUtils.isNullOrEmptyOrBlank(id)) {
                    this.storedPKs.add(id);
                }
            }
        }

        return continueParsing;
    }

    /**
     * TODO
     * @param study
     * @param studyTitle
     */
    public void parseStudyTitle(Item study, String studyTitle, String acronym) throws Exception {
        boolean displayTitleSet = false;

        // TODO: && !title.equals("-") && !title.equals("_") && !title.equals(".") ?
        /* Public title */
        if (!ConverterUtils.isNullOrEmptyOrBlank(studyTitle)) {
            study.setAttributeIfNotNull("displayTitle", studyTitle);
            displayTitleSet = true;

            this.createAndStoreClassItem(study, "Title",
                new String[][]{{"text", studyTitle}, {"type", ConverterCVT.TITLE_TYPE_PUBLIC}});
        }
        
        /* Acronym */
        if (!ConverterUtils.isNullOrEmptyOrBlank(acronym)) {
            if (!displayTitleSet) {
                study.setAttributeIfNotNull("displayTitle", acronym);
            }

            this.createAndStoreClassItem(study, "Title",
                new String[][]{{"text", acronym}, {"type", ConverterCVT.TITLE_TYPE_ACRONYM}});
        }
        
        // Unknown title if not set before
        if (!displayTitleSet) {
            study.setAttribute("displayTitle", ConverterCVT.TITLE_UNKNOWN);
        }
    }

    /**
     * TODO
     * @param study
     * @param briefSummary
     */
    public void parseBriefSummary(Item study, String briefSummary) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(briefSummary)) {
            study.setAttribute("briefDescription", briefSummary);
        }
    }

    public void parseStatus(Item study, String status) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(status)) {
            // TODO: normalisation
            String cleanedStatus = ConverterUtils.capitaliseAndReplaceCharBySpace(status, '_');
            if (cleanedStatus.equals("Active not recruiting")) { // Temporary
                cleanedStatus = ConverterCVT.STATUS_ACTIVE_NOT_RECRUITING;
            }
            study.setAttributeIfNotNull("status", cleanedStatus);
        }
    }

    /**
     * TODO
     * @param study
     * @param conditionsStr
     * @throws Exception
     */
    public void parseConditions(Item study, String conditionsStr) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(conditionsStr)) {
            String[] conditions = conditionsStr.split("\\|");
            for (String condition: conditions) {
                if (!ConverterUtils.isNullOrEmptyOrBlank(condition)) {
                    // TODO: link/normalise with CV
                    this.createAndStoreClassItem(study, "StudyCondition", 
                        new String[][]{{"originalValue", WordUtils.capitalizeFully(condition, ' ', '-')}});
                }
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param interventionsStr
     * @throws Exception
     */
    public void parseInterventions(Item study, String interventionsStr) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(interventionsStr)) {
            // TODO: formatting
            study.setAttributeIfNotNull("interventions", interventionsStr);

            String[] interventions = interventionsStr.split("\\|");
            String[] tuple;
            for (String intervention: interventions) {
                if (!ConverterUtils.isNullOrEmptyOrBlank(intervention)) {
                    tuple = intervention.split(": ");
                    if (tuple.length == 2) {
                        // TODO: link/normalise with CV
                        this.createAndStoreClassItem(study, "Topic",
                            new String[][]{{"type", ConverterUtils.capitaliseAndReplaceCharBySpace(tuple[0], '_')}, 
                                {"value", WordUtils.capitalizeFully(tuple[1], ' ', '-')}});
                    } else {
                        this.writeLog("Failed to properly split intervention tuple: " + intervention + "; full string: " + interventionsStr);
                    }
                }
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param primaryOutcomes
     */
    public void parsePrimaryOutcomes(Item study, String primaryOutcomes) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(primaryOutcomes)) {
            study.setAttributeIfNotNull("primaryOutcome", primaryOutcomes);
        }
    }

    /**
     * TODO
     * @param study
     * @param secondaryOutcomes
     * @param otherOutcomes
     */
    public void parseSecondaryOutcomes(Item study, String secondaryOutcomes, String otherOutcomes) {
        // TODO: capitalise?
        StringBuilder outcomesSb = new StringBuilder();

        if (!ConverterUtils.isNullOrEmptyOrBlank(secondaryOutcomes)) {
            outcomesSb.append(secondaryOutcomes);
        }

        if (!ConverterUtils.isNullOrEmptyOrBlank(otherOutcomes)) {
            if (!outcomesSb.toString().isEmpty()) {
                if (!outcomesSb.toString().endsWith(".")) {
                    outcomesSb.append(".");
                }
                outcomesSb.append(" ");
            }
            outcomesSb.append(otherOutcomes);
        }
        
        study.setAttributeIfNotNull("secondaryOutcomes", outcomesSb.toString());
    }

    /**
     * TODO
     * @param study
     * @param sponsor
     * @throws Exception
     */
    public void parseSponsor(Item study, String sponsor) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(sponsor)) {
            // TODO: differentiate people from orgs + link to CV
            this.createAndStoreClassItem(study, "Organisation",
                new String[][]{{"contribType", ConverterCVT.CONTRIBUTOR_TYPE_SPONSOR}, {"name", sponsor}});
        }
    }

    /**
     * TODO
     * @param study
     * @param collaboratorsStr
     * @throws Exception
     */
    public void parseCollaborators(Item study, String collaboratorsStr) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(collaboratorsStr)) {
            String[] collaborators = collaboratorsStr.split("\\|");
            for (String collaborator: collaborators) {
                // TODO: differentiate people from orgs (if any) + link to CV
                this.createAndStoreClassItem(study, "Organisation",
                    new String[][]{{"contribType", ConverterCVT.CONTRIBUTOR_TYPE_COLLABORATING_ORG}, {"name", collaborator}});
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param gender
     */
    public void parseGender(Item study, String gender) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(gender)) {
            if (gender.equalsIgnoreCase(ConverterCVT.GENDER_ALL)) {
                study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_ALL);
            } else if (gender.equalsIgnoreCase(ConverterCVT.GENDER_WOMEN)) {
                study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_WOMEN);
            } else if (gender.equalsIgnoreCase(ConverterCVT.GENDER_MEN)) {
                study.setAttributeIfNotNull("genderElig", ConverterCVT.GENDER_MEN);
            } else {
                this.writeLog("Unknown gender value: " + gender);
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param ageStr
     */
    public void parseAge(Item study, String ageStr) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(ageStr)) {
            int minAge = Integer.MAX_VALUE;
            int maxAge = Integer.MIN_VALUE;

            String[] ageRanges = ageStr.split(", ");

            // Parsing the various age ranges
            for (String ageRange: ageRanges) {
                if (ageRange.equalsIgnoreCase(CtgConverter.AGE_CHILD)) {    // "CHILD" (0-17)
                    minAge = Integer.MIN_VALUE;
                    if (maxAge < 17) {
                        maxAge = 17;
                    }
                } else if (ageRange.equalsIgnoreCase(CtgConverter.AGE_ADULT)) { // "ADULT" (18-64)
                    if (minAge > 18) {
                        minAge = 18;
                    }
                    if (maxAge < 64) {
                        maxAge = 64;
                    }
                } else if (ageRange.equalsIgnoreCase(CtgConverter.AGE_OLDER_ADULT)) {   // "OLDER_ADULT" (65+)
                    if (minAge > 65) {
                        minAge = 65;
                    }
                    maxAge = Integer.MAX_VALUE;
                } else {
                    this.writeLog("Unknown age range value: " + ageRange + ", full string: " + ageStr);
                }
            }

            // TODO: none on no minimum?
            if (minAge != Integer.MIN_VALUE && minAge != Integer.MAX_VALUE) {
                study.setAttributeIfNotNull("minAge", String.valueOf(minAge));
                study.setAttributeIfNotNull("minAgeUnit", ConverterCVT.AGE_UNIT_YEARS);
            }
            
            if (maxAge != Integer.MIN_VALUE && maxAge != Integer.MAX_VALUE) {
                study.setAttributeIfNotNull("maxAge", String.valueOf(maxAge));
                study.setAttributeIfNotNull("maxAgeUnit", ConverterCVT.AGE_UNIT_YEARS);
            }
        } else {
            study.setAttributeIfNotNull("minAge", ConverterCVT.UNKNOWN);
            study.setAttributeIfNotNull("maxAge", ConverterCVT.UNKNOWN);
        }
    }

    /**
     * TODO
     * @param study
     * @param phasesStr
     * @throws Exception
     */
    public void parsePhases(Item study, String phasesStr) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(phasesStr)) {
            Matcher mPhase = P_PHASE.matcher(phasesStr);
            if (mPhase.matches()) {
                String phaseValue = "";

                String na = mPhase.group(1);
                String early = mPhase.group(2);
                String p1 = mPhase.group(3);
                String p2 = mPhase.group(4);

                if (na != null) {   // Not applicable
                    phaseValue = ConverterCVT.NOT_APPLICABLE;
                } else {
                    if (early != null) {
                        phaseValue = ConverterCVT.FEATURE_V_EARLY_PHASE_1;
                    } else if (p2 == null) {    // One phase number
                        phaseValue = ConverterUtils.convertPhaseNumber(p1);
                    } else {    // Two phase numbers
                        phaseValue = ConverterUtils.constructMultiplePhasesString(p1, p2);
                    }
                }

                this.createAndStoreClassItem(study, "StudyFeature", 
                    new String[][]{{"featureType", ConverterCVT.FEATURE_T_PHASE}, {"featureValue", phaseValue}});
            } else {
                this.writeLog("Failed to match phase value: " + phasesStr);
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param enrolment
     */
    public void parseEnrolment(Item study, String enrolment) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(enrolment)) {
            String studyStatus = ConverterUtils.getValueOfItemAttribute(study, "status");
            // Note: Enrolment can also be actual enrolment for suspended status (and perhaps others as well),
            // but there does not seem to be a way to know that from the data, it is shown on CTG website however
            if (studyStatus.equalsIgnoreCase(ConverterCVT.STATUS_COMPLETED) || studyStatus.equalsIgnoreCase(ConverterCVT.STATUS_ACTIVE_NOT_RECRUITING)) {
                study.setAttributeIfNotNull("actualEnrolment", enrolment);
            } else {
                study.setAttributeIfNotNull("plannedEnrolment", enrolment);
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param studyType
     */
    public void parseStudyType(Item study, String studyType) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(studyType)) {
            study.setAttributeIfNotNull("type", ConverterUtils.capitaliseAndReplaceCharBySpace(studyType, '_'));
        }
    }

    /**
     * TODO
     * @param study
     * @param studyDesign
     * @throws Exception
     */
    public void parseStudyDesign(Item study, String studyDesign) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(studyDesign)) {
            // Observational studies always have "Observational Model: |Time Perspective: p" as study design value
            if (!studyDesign.startsWith("Observational")) {
                String[] kvs = studyDesign.split("\\|");
                String[] tuple;

                if (kvs.length == 4) {
                    for (String kv: kvs) {
                        tuple = kv.split(": ");
                        if (tuple.length == 2) {
                            // Filtering out "NA" value for "Allocation" feature type
                            if (!tuple[0].isEmpty() && !tuple[1].isEmpty() && !tuple[1].equalsIgnoreCase("NA")) {
                                // TODO: CV relevant? CV in MDR for features is entirely based on CTG values
                                // TODO: normalise masking values?
                                this.createAndStoreClassItem(study, "StudyFeature", 
                                    new String[][]{{"featureType", ConverterUtils.capitaliseFirstLetter(tuple[0])},
                                                    {"featureValue", ConverterUtils.capitaliseAndReplaceCharBySpace(tuple[1], '_')}});
                            } else {
                                this.writeLog("parseStudyDesign(): key is empty, tuple: " + kv + "; full string: " + studyDesign);
                            }
                        }
                    }
                } else {
                    this.writeLog("Unexpected length for study design split features (" + kvs.length + "), full string: " + studyDesign);
                }
            }
        }

        /*
         * Allocation: randomized, na, non_randomized
         * Masking: none, single, double, triple, quadruple (potentially with details in parentheses)
         * Intervention model: parallel, single_group, crossover, sequential, factorial
         * Primary purpose: treatment, prevention, other, supportive_care, basic_science, diagnostic, health_services_research, screening, device_feasibility, ect (maybe more?)
         */
    }

    /**
     * TODO
     * @param study
     * @param startDateStr
     */
    public void parseStartDate(Item study, String startDateStr) {
        study.setAttributeIfNotNull("startDate", startDateStr);
    }
    
    /**
     * TODO
     * @param study
     * @param completionDateStr
     * @param primaryCompletionDateStr
     */
    public void parseCompletionDates(Item study, LocalDate completionDate, LocalDate primaryCompletionDate) {
        if (completionDate != null) {
            study.setAttributeIfNotNull("endDate", completionDate.toString());
        } else if (primaryCompletionDate != null) {
            study.setAttributeIfNotNull("endDate", primaryCompletionDate.toString());
        }
    }

    /**
     * TODO
     * @param study
     * @param locationsStr
     * @throws Exception
     */
    public void parseLocations(Item study, String locationsStr) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(locationsStr)) {
            String[] splitLocations = locationsStr.split("\\|");
            String[] splitLocation;
            String countryName, cityName, facility;

            if (splitLocations.length > 0) {
                for (String location: splitLocations) {
                    countryName = "";
                    cityName = "";
                    facility = "";

                    splitLocation = location.split(", ");
                    if (splitLocation.length < 2) {
                        this.writeLog("Failed to split location: " + location);
                    } else if (splitLocation.length == 2) { // City/state/province/region, country
                        // TODO check if city or state/province/region + clean
                        // TODO 
                        // if (!splitLocation[0].toLowerCase().contains("locations")) {    // Filtering "Many locations" and "Multiple locations"
                        // }
                        countryName = WordUtils.capitalizeFully(splitLocation[1], ' ', '-');
                    } else if (splitLocation.length == 3) { // Place, city, country
                        facility = CtgConverter.cleanLocationSubstring(splitLocation[0]);
                        cityName = CtgConverter.cleanLocationSubstring(splitLocation[1]);
                        countryName = CtgConverter.cleanCountryString(splitLocation[1], splitLocation[2]);
                    } else if (splitLocation.length == 4) { // Place, city, postal code, country
                        facility = WordUtils.capitalizeFully(splitLocation[0], ' ', '-');
                        cityName = WordUtils.capitalizeFully(splitLocation[1], ' ', '-');
                        countryName = CtgConverter.cleanCountryString(splitLocation[2], splitLocation[3]);
                    } else if (splitLocation.length == 5) { // Place, city, state, postal code, country (to check)
                        facility = WordUtils.capitalizeFully(splitLocation[0], ' ', '-');
                        cityName = WordUtils.capitalizeFully(splitLocation[1], ' ', '-');
                        countryName = WordUtils.capitalizeFully(splitLocation[4], ' ', '-');
                        countryName = CtgConverter.cleanCountryString(splitLocation[3], splitLocation[4]);
                    } else if (splitLocation.length == 6) { // Place, street, city, state, postal code, country
                        cityName = WordUtils.capitalizeFully(splitLocation[2], ' ', '-');
                        countryName = WordUtils.capitalizeFully(splitLocation[5], ' ', '-');
                        countryName = CtgConverter.cleanCountryString(splitLocation[4], splitLocation[5]);
                    } else if (splitLocation.length > 6) {
                        this.writeLog("Unexpected number of substrings in split location: " + location);
                    }

                    if (!ConverterUtils.isNullOrEmptyOrBlank(countryName)) {
                        this.createAndStoreClassItem(study, "Location", 
                            new String[][]{{"countryName", countryName}, {"cityName", cityName}, {"facility", facility}});
                    }
                    /*
                        National Institutes of Health Clinical Center, 9000 Rockville Pike, Bethesda, Maryland, 20892, United States
                        Seoul National University Hospital, Seoul, Korea, Republic of
                        Medical University of Vienna, Vienna, 1090, Austria
                        Hacettepe University, Ankara, Turkey
                        Many Locations, Germany
                     */

                    /*
                        <reference name="study" referenced-type="Study" reverse-reference="locations"/>
                        <reference name="country" referenced-type="Country"/>
                        <attribute name="facilityOrg" type="java.lang.String"/>
                        <attribute name="facility" type="java.lang.String"/>
                        <attribute name="facilityRor" type="java.lang.String"/>
                        <!-- TODO: CV? -->
                        <attribute name="city" type="java.lang.String"/>
                        <attribute name="cityName" type="java.lang.String"/>
                        <attribute name="countryName" type="java.lang.String"/>
                        <attribute name="status" type="java.lang.String"/>
                    */
                }
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param studyDocuments
     * @throws Exception
     */
    public void parseStudyDocuments(Item study, String studyDocuments) throws Exception {
        if (!ConverterUtils.isNullOrEmptyOrBlank(studyDocuments)) {
            Item documentDO;
            String objectType;
            String[] splitDocuments = studyDocuments.split("\\|");
            Deque<String> objectTypes = new ArrayDeque<String>();

            for (String doc: splitDocuments) {
                // Matching title + URL
                Matcher mDoc = P_DOC.matcher(doc);
                if (mDoc.matches()) {
                    String g1 = mDoc.group(1);
                    String g1Lower = g1.toLowerCase();
                    String g2 = mDoc.group(2);  // Direct link to study documents (study protocols, SAPs, ICFs)

                    // Object type(s), one document may combine multiple types (e.g. protocol + ICF)
                    if (g1Lower.contains("informed consent form")) {
                        objectTypes.add(ConverterCVT.O_TYPE_INFORMED_CONSENT_FORM);
                    }
                    if (g1Lower.contains("study protocol")) {
                        objectTypes.add(ConverterCVT.O_TYPE_STUDY_PROTOCOL);
                    }
                    if (g1Lower.contains("statistical analysis plan")) {
                        objectTypes.add(ConverterCVT.O_TYPE_STATISTICAL_ANALYSIS_PLAN);
                    }

                    if (objectTypes.isEmpty()) {
                        this.writeLog("Unknown study document type: " + g1Lower);
                    } else if (!g1Lower.isEmpty()) {
                        while (!objectTypes.isEmpty()) {
                            objectType = objectTypes.pop();
                            // TODO: title useless? see mdr.xml comment
                            // Document DO
                            documentDO = this.createAndStoreClassItem(study, "DataObject", 
                                new String[][]{{"type", objectType}, {"objectClass", ConverterCVT.O_CLASS_TEXT}, {"title", g1}});
    
                            // DO Instance with direct URL
                            this.createAndStoreClassItem(documentDO, "ObjectInstance", new String[][]{{"url", g2}});
                        }
                    }
                } else {
                    this.writeLog("Failed to match study document string: " + doc);
                }
            }
        }
    }

    /**
     * TODO
     * @param study
     * @param creationDate
     * @param url
     * @throws Exception
     */
    public void createAndStoreRegistryEntryDO(Item study, String entryUrl, LocalDate firstPosted, LocalDate lastUpdate) throws Exception {
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
        // TODO: publication year?

        /* Registry entry instance */
        if (!ConverterUtils.isNullOrEmptyOrBlank(this.currentTrialID)) {
            this.createAndStoreClassItem(doRegistryEntry, "ObjectInstance", 
                new String[][]{{"url", entryUrl}, {"resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT}});
        }

        /* Object created date */
        if (firstPosted != null) {
            this.createAndStoreObjectDate(doRegistryEntry, firstPosted, ConverterCVT.DATE_TYPE_AVAILABLE);
        }

        // Last update
        if (lastUpdate != null) {
            this.createAndStoreObjectDate(doRegistryEntry, lastUpdate, ConverterCVT.DATE_TYPE_UPDATED);
        }
    }

    /**
     * TODO
     * @param study
     * @param studyResults
     * @param entryURL
     * @param completionDate
     * @param primaryCompletionDate
     * @param resultsFirstPosted
     * @param lastUpdate
     * @throws Exception
     */
    public void createAndStoreResultsSummaryDO(Item study, String studyResults, String entryURL, 
        LocalDate completionDate, LocalDate primaryCompletionDate, LocalDate resultsFirstPosted, LocalDate lastUpdate) throws Exception {
        
        // Using results field (yes/no) to create or not results summary DO
        if (studyResults.equalsIgnoreCase("yes") && !ConverterUtils.isNullOrEmptyOrBlank(entryURL) && resultsFirstPosted != null) {
            // Constructing results URL by prepending results suffix to entry URL
            String resultsURLLink = entryURL + "?tab=results";

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
                                        new String[][]{{"url", resultsURLLink}, {"resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT}});
            
            // Results completed date
            if (completionDate != null) {
                this.createAndStoreObjectDate(resultsSummaryDO, completionDate, ConverterCVT.DATE_TYPE_CREATED);
            } else if (primaryCompletionDate != null) {
                this.createAndStoreObjectDate(resultsSummaryDO, primaryCompletionDate, ConverterCVT.DATE_TYPE_CREATED);
            }

            // Results posted date
            if (resultsFirstPosted != null) {
                this.createAndStoreObjectDate(resultsSummaryDO, resultsFirstPosted, ConverterCVT.DATE_TYPE_AVAILABLE);
                // Publication year
                String publicationYear = String.valueOf(resultsFirstPosted.getYear());
                if (!ConverterUtils.isNullOrEmptyOrBlank(publicationYear)) {
                    resultsSummaryDO.setAttributeIfNotNull("publicationYear", publicationYear);
                }
            }

            // Last update
            if (lastUpdate != null) {
                this.createAndStoreObjectDate(resultsSummaryDO, lastUpdate, ConverterCVT.DATE_TYPE_UPDATED);
            }
        }
    }
    
    /**
     * TODO
     * @param s
     * @return
     */
    public static String cleanLocationSubstring(String s) {
        s = s.toLowerCase();
        if (!s.contains("multiple locations") && !s.contains("many locations") && !s.contains("multiple sites") && !s.contains("many facilities")) {
            return WordUtils.capitalizeFully(s);
        }
        return "";
    }
    
    /**
     * TODO
     * @param c1 either part of the country (e.g. "Korea"), or city name
     * @param c2 either part of the country (e.g. "Republic Of") or full country name
     * @return
     */
    public static String cleanCountryString(String c1, String c2) {
        String countryName = "";
        if (c2.equalsIgnoreCase("Republic Of") || c2.equalsIgnoreCase("Islamic Republic of")
             || c2.equalsIgnoreCase("The Democratic Republic of the") || c2.equalsIgnoreCase("The Former Yugoslav Republic of")) {
            countryName = WordUtils.capitalizeFully(c1 + ", " + c2, ' ', '-');
        } else {
            countryName = c2;
        }
        return countryName;
    }

    /**
     * Add -01 prefix to date only composed of year + month
     * @param dateString
     * @return
     */
    public static String normaliseDateString(String dateString) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(dateString) && dateString.length() == 7) {
            dateString = dateString + "-01";
        }
        return dateString;
    }

    /**
     * Get field value from array of values using a field's position-lookup Map, value is also cleaned.
     * 
     * @param lineValues the list of all values for a line in the data file
     * @param field the name of the field to get the value of
     * @return the cleaned value of the field
     * @see //#cleanValue()
     */
    public String getAndCleanValue(String[] lineValues, String field) {
        int fieldInd = this.fieldsToInd.get(field);
        if (fieldInd < lineValues.length) {
            return this.cleanValue(lineValues[this.fieldsToInd.get(field)], true);
        } else {
            this.writeLog("Field index " + fieldInd + 
                " out of bounds this study's values (length: " + lineValues.length + ")");
            return null;
        }
    }

    public String cleanValue(String s, boolean strip) {
        // TODO: unescape HTML
        if (strip) {
            // return ConverterUtils.unescapeHtml(ConverterUtils.removeQuotes(s)).strip();
            return ConverterUtils.removeQuotes(s).strip();
        }
        return ConverterUtils.removeQuotes(s);
    }

    /**
     * TODO
     * 
     * @return map of data file field names and their corresponding column index
     */
    public Map<String, Integer> getHeaders(CSVReader csvReader) throws Exception {
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();
        String[] fields = csvReader.readNext();

        if (fields.length == 0) {
            throw new Exception("CTG data file is empty (failed getting headers)");
        }

        for (int ind = 0; ind < fields.length; ind++) {
            fieldsToInd.put(fields[ind], ind);
        }

        return fieldsToInd;
    }
}
