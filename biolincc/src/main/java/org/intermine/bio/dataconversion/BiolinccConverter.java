package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.ObjectInstance;

import org.apache.commons.text.WordUtils;
import org.apache.xalan.xsltc.compiler.sym;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

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

/**
 * 
 * @author
 */
public class BiolinccConverter extends BaseConverter {
    //
    private static final String DATASET_TITLE = "BioLINCC full";
    private static final String DATA_SOURCE_NAME = "BioLINCC";

    private static final Pattern P_HEADER = Pattern.compile("\\w+.*");
    private static final Pattern P_STUDY_YEARS = Pattern.compile("^([\\d+]{4}|Ongoing).*([\\d+]{4}|Ongoing).*$");

    private static final String CTG_STUDY_BASE_URL = "https://clinicaltrials.gov/study/";
    private static final String AGE_ADULT = "Adult";
    private static final String AGE_PEDIATRIC = "Pediatric";
    private static final String AGE_ALL = "Both";
    private static final String STUDY_YEAR_ONGOING = "Ongoing";
    private static final String STUDY_TYPE_CLINICAL_TRIAL = "Clinical Trial";
    private static final String STUDY_TYPE_EPIDEMIOLOGY_STUDY = "Epidemiology Study";
    private static final String STUDY_TYPE_MIXED = "Clinical Trial/Epidemiology Study";
    private static final String STUDY_TYPE_PROGRAM_EVALUATION = "Program Evaluation";

    private Set<String> seenNctIds = new HashSet<String>();
    private Map<String, Integer> fieldsToInd;

    /**
     * Constructor
     * 
     * @param writer the ItemWriter used to handle the resultant items
     * @param model  the Model
     */
    public BiolinccConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /*
         * Opened BufferedReader is passed as argument (from
         * FileConverterTask.execute())
         */
        this.startLogging("biolincc");

        final CSVParser parser = new CSVParserBuilder()
                .withSeparator(',')
                // TODO check if withquotechar working, otherwise needs removeQuotes
                .withQuoteChar('"')
                .build();
        final CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(parser)
                .build();

        /* Headers */
        this.fieldsToInd = this.getHeaders(csvReader.readNext());

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

        /* Adding this source */
        this.createAndStoreClassItem(study, "StudySource", new String[][] { { "name", DATA_SOURCE_NAME } });

        /* Study title */
        String studyName = this.getAndCleanValue(lineValues, "Study Name");
        // Study acronym
        String acronym = this.getAndCleanValue(lineValues, "Acronym");
        this.parseStudyTitle(study, studyName, acronym);

        // Study ID Note: parsing ID after title to log title if ID is empty
        /* StudyIdentifier: BioLINCC ID */
        String biolinccID = this.getAndCleanValue(lineValues, "Accession Number");
        this.parseID(study, biolinccID);

        // Unused, not in our model? (says if biolincc study or not)
        String collectionType = this.getAndCleanValue(lineValues, "Collection Type");

        String background = this.getAndCleanValue(lineValues, "Background");
        String objectives = this.getAndCleanValue(lineValues, "Objectives");
        // TODO: other fields to construct description?
        this.constructBriefDescription(study, background, objectives);

        /* BioLINCC registry entry */
        String biolinccUrl = this.getAndCleanValue(lineValues, "BioLINCC URL");

        // Unused, not in our model
        String covidStudyClassification = this.getAndCleanValue(lineValues, "COVID study classification");

        /* CTG registry entries */
        String clinicalTrialUrls = this.getAndCleanValue(lineValues, "Clinical trial urls");
        // study.setAttributeIfNotNull("testField3", clinicalTrialUrls);

        /* Registry entry DOs */
        // Also sets Study.nctID (should probably be moved outside of this function)
        this.createAndStoreRegistryEntryDO(study, biolinccUrl, clinicalTrialUrls);

        /* Study age IC */
        String cohortType = this.getAndCleanValue(lineValues, "Cohort type");
        this.parseAge(study, cohortType);

        /* Study primary outcome(s) */
        String conclusions = this.getAndCleanValue(lineValues, "Conclusions");
        this.parseConclusions(study, conclusions);

        /* Study conditions */
        String conditions = this.getAndCleanValue(lineValues, "Conditions");
        this.parseConditions(study, conditions);

        // TODO: unused free text
        String design = this.getAndCleanValue(lineValues, "Design");
        // study.setAttributeIfNotNull("testField1", design);

        // Unused, "Has Specimens" and "Has Study Datasets" have the same information
        String availableResources = this.getAndCleanValue(lineValues, "Available resources");
        // Unused, unsure what this indicates
        String isPublicUseDataset = this.getAndCleanValue(lineValues, "Is public use dataset");

        // Used for both clinical datasets and specimens datasets
        String specificConsentRestrictions = this.getAndCleanValue(lineValues, "Specific Consent Restrictions");

        /* DataObject + ObjDataset for clinical data */
        String commercialUseDataRestrictions = this.getAndCleanValue(lineValues, "Commercial use data restrictions");
        String dataRestrictionsBasedOnAreaOfResearch = this.getAndCleanValue(lineValues,
                "Data restrictions based on area of research");
        String irbApprovalRequiredForData = this.getAndCleanValue(lineValues, "IRB approval required for data");
        String studyOpenDateData = this.getAndCleanValue(lineValues, "Study Open Date (Data)");
        String hasStudyDatasets = this.getAndCleanValue(lineValues, "Has Study Datasets");
        try {
            if (ConverterUtils.booleanFromString(hasStudyDatasets)) {
                this.parseClinicalDatasets(study, specificConsentRestrictions, commercialUseDataRestrictions,
                        dataRestrictionsBasedOnAreaOfResearch,
                        irbApprovalRequiredForData, studyOpenDateData);
            }
        } catch (ParseException e) {
            this.writeLog("Error: unexpected value in 'Has Study Datasets': " + hasStudyDatasets);
        }

        /* DataObject + ObjDataset for biospecimens */
        String commercialUseSpecimenRestrictions = this.getAndCleanValue(lineValues,
                "Commercial use specimen restrictions");
        String nonGeneticUseSpecimenRestrictions = this.getAndCleanValue(lineValues,
                "Non-genetic use specimen restrictions based on area of use");
        String geneticUseAreaOfResearchRestrictions = this.getAndCleanValue(lineValues,
                "Genetic use area of research restrictions");
        String geneticUseOfSpecimensAllowed = this.getAndCleanValue(lineValues, "Genetic use of specimens allowed?");
        String materialTypes = this.getAndCleanValue(lineValues, "Material Types");
        String studyOpenDateSpecimens = this.getAndCleanValue(lineValues, "Study Open Date (Specimens)");
        String hasSpecimens = this.getAndCleanValue(lineValues, "Has Specimens");
        try {
            if (ConverterUtils.booleanFromString(hasSpecimens)) {
                this.parseBiospecimen(study, specificConsentRestrictions, commercialUseSpecimenRestrictions,
                        nonGeneticUseSpecimenRestrictions, geneticUseAreaOfResearchRestrictions,
                        geneticUseOfSpecimensAllowed, materialTypes, studyOpenDateSpecimens);
            }
        } catch (ParseException e) {
            this.writeLog("Error: unexpected value in 'Has Specimens': " + hasSpecimens);
        }

        // Unused
        String extraStudyDetails = this.getAndCleanValue(lineValues, "Extra Study Details");

        // TODO: StudyCondition?
        String hivStudyClassification = this.getAndCleanValue(lineValues, "HIV study classification");

        // ? Unused
        String ingestionStatus = this.getAndCleanValue(lineValues, "Ingestion Status");

        // TODO: create Organisation item?
        String nhlbiDivision = this.getAndCleanValue(lineValues, "NHLBI Division");

        /* Organisation: clinical trial network/research program */
        String network = this.getAndCleanValue(lineValues, "Network");
        this.parseNetwork(study, network);

        /* Person: scientific contact */
        String parentStudyContactEmail = this.getAndCleanValue(lineValues, "Parent Study Contact Email");
        String parentStudyContactName = this.getAndCleanValue(lineValues, "Parent Study Contact Name");
        this.parseStudyContact(study, parentStudyContactEmail, parentStudyContactName);

        /* Study IEC */
        // TODO: also contains number of participants
        String participants = this.getAndCleanValue(lineValues, "Participants");
        this.parseIEC(study, participants);

        String publicationUrls = this.getAndCleanValue(lineValues, "Publication urls");
        String publications = this.getAndCleanValue(lineValues, "Publications");
        // study.setAttributeIfNotNull("testField6", publicationUrls);
        // study.setAttributeIfNotNull("testField7", publications);
        this.parsePublications(study, publicationUrls, publications);

        // TODO: use for Relationship items (for now not really possible)
        String relatedStudies = this.getAndCleanValue(lineValues, "Related studies");
        // study.setAttributeIfNotNull("testField2", relatedStudies);

        /* Study website DO */
        String studyWebsite = this.getAndCleanValue(lineValues, "Study Website");
        this.parseStudyWebsite(study, studyWebsite);

        /* Study start and end dates */
        String studyYears = this.getAndCleanValue(lineValues, "Study Years");
        String studyPeriod = this.getAndCleanValue(lineValues, "Study period");
        this.parseStudyYears(study, studyYears, studyPeriod);

        String studyType = this.getAndCleanValue(lineValues, "Study type");
        this.parseStudyType(study, studyType);

        store(study);
        this.currentTrialID = null;
    }

    /**
     * TODO
     * 
     * @param study
     * @param biolinccID
     */
    public void parseID(Item study, String biolinccID) throws Exception {
        if (!ConverterUtils.isBlankOrNull(biolinccID)) {
            this.createAndStoreClassItem(study, "StudyIdentifier",
                    new String[][] { { "identifierValue", biolinccID } });
            this.currentTrialID = biolinccID;
        } else {
            this.writeLog("Encountered study with no ID, title: " + ConverterUtils.getAttrValue(study, "displayTitle"));
        }
    }

    /**
     * TODO
     * Same as CTG
     * 
     * @param study
     * @param studyTitle
     */
    public void parseStudyTitle(Item study, String studyTitle, String acronym) throws Exception {
        boolean displayTitleSet = false;

        /* Public title */
        if (!ConverterUtils.isBlankOrNull(studyTitle)) {
            study.setAttributeIfNotNull("displayTitle", studyTitle);
            displayTitleSet = true;

            this.createAndStoreClassItem(study, "Title",
                    new String[][] { { "text", studyTitle }, { "type", ConverterCVT.TITLE_TYPE_PUBLIC } });
        }

        /* Acronym */
        if (!ConverterUtils.isBlankOrNull(acronym)) {
            if (!displayTitleSet) {
                study.setAttributeIfNotNull("displayTitle", acronym);
            }

            this.createAndStoreClassItem(study, "Title",
                    new String[][] { { "text", acronym }, { "type", ConverterCVT.TITLE_TYPE_ACRONYM } });
        }

        // Unknown title if not set before
        if (!displayTitleSet) {
            study.setAttribute("displayTitle", ConverterCVT.TITLE_UNKNOWN);
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param background
     * @param objectives
     */
    public void constructBriefDescription(Item study, String background, String objectives) {
        StringBuilder sb = new StringBuilder();

        // Background
        if (!ConverterUtils.isBlankOrNull(background)) {
            sb.append("Background: ");
            sb.append(background);
        }

        // Objectives
        if (!ConverterUtils.isBlankOrNull(objectives)) {
            if (!sb.toString().isEmpty()) {
                if (!sb.toString().endsWith(".")) {
                    sb.append(".");
                }
                sb.append(" ");
            }
            sb.append("Objectives: ");
            sb.append(background);
        }

        study.setAttributeIfNotNull("briefDescription", sb.toString());
    }

    /**
     * TODO
     * 
     * @param study
     * @param cohortType
     */
    public void parseAge(Item study, String cohortType) {
        if (!ConverterUtils.isBlankOrNull(cohortType)) {
            String minAge = "";
            String maxAge = "";

            if (cohortType.equalsIgnoreCase(BiolinccConverter.AGE_ADULT)) {
                minAge = "18";
            } else if (cohortType.equalsIgnoreCase(BiolinccConverter.AGE_PEDIATRIC)) {
                maxAge = "17";
            } else if (!cohortType.equalsIgnoreCase(BiolinccConverter.AGE_ALL)) {
                this.writeLog("Unknown cohort type value: " + cohortType);
                // TODO: unknown value
            }

            // TODO: none on no minimum?
            if (!ConverterUtils.isBlankOrNull(minAge)) {
                study.setAttributeIfNotNull("minAge", minAge);
                study.setAttributeIfNotNull("minAgeUnit", ConverterCVT.AGE_UNIT_YEARS);
            }

            if (!ConverterUtils.isBlankOrNull(maxAge)) {
                study.setAttributeIfNotNull("maxAge", maxAge);
                study.setAttributeIfNotNull("maxAgeUnit", ConverterCVT.AGE_UNIT_YEARS);
            }
        } else {
            study.setAttributeIfNotNull("minAge", ConverterCVT.UNKNOWN);
            study.setAttributeIfNotNull("maxAge", ConverterCVT.UNKNOWN);
        }
    }

    /**
     * TODO
     * Also sets Study.nctID
     * 
     * @param study
     * @param clinicalTrialUrls
     * @return
     */
    public List<String> parseClinicalTrialUrls(Item study, String clinicalTrialUrls) {
        Set<String> parsedUrls = new HashSet<String>();

        if (!ConverterUtils.isBlankOrNull(clinicalTrialUrls)) {
            String[] urls = clinicalTrialUrls.split(", ");
            Matcher mUrl;
            String ctgUrl;

            for (String url : urls) {
                mUrl = ConverterUtils.P_ID_AT_END_OF_URL.matcher(url);
                if (mUrl.matches()) {
                    String nctId = mUrl.group(1);
                    if (!this.seenNctIds.contains(nctId)) {
                        this.seenNctIds.add(nctId);
                        if (ConverterUtils.isBlankOrNull(ConverterUtils.getAttrValue(study, "nctID"))) {
                            study.setAttribute("nctID", nctId);
                        } else {
                            this.writeLog("NCT ID found (" + nctId + ") for study which already has an NCT ID: "
                                    + ConverterUtils.getAttrValue(study, "nctID"));
                        }
                    } else {
                        this.writeLog("NCT ID already seen: " + nctId);
                    }

                    ctgUrl = CTG_STUDY_BASE_URL + nctId;
                    parsedUrls.add(ctgUrl);
                } else {
                    this.writeLog("Failed to match CTG URL: " + url);
                }
            }
        }

        return new ArrayList<String>(parsedUrls);
    }

    public void createAndStoreRegistryEntryDO(Item study, String biolinccUrl, String clinicalTrialUrls)
            throws Exception {
        // Display title
        String studyDisplayTitle = ConverterUtils.getAttrValue(study, "displayTitle");
        String doDisplayTitle;
        if (!ConverterUtils.isBlankOrNull(studyDisplayTitle)) {
            doDisplayTitle = studyDisplayTitle + " - " + ConverterCVT.O_TITLE_REGISTRY_ENTRY;
        } else {
            doDisplayTitle = ConverterCVT.O_TITLE_REGISTRY_ENTRY;
        }

        /* Registry entry DO */
        Item doRegistryEntry = this.createAndStoreClassItem(study, "DataObject",
                new String[][] { { "title", doDisplayTitle }, { "objectClass", ConverterCVT.O_CLASS_TEXT },
                        { "type", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY } });

        /* Biolincc registry entry DO instance */
        this.createAndStoreClassItem(doRegistryEntry, "ObjectInstance",
                new String[][] { { "url", biolinccUrl }, { "resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT } });

        /* CTG Registry entry DO instances */
        List<String> ctgUrls = this.parseClinicalTrialUrls(study, clinicalTrialUrls);
        for (String ctgUrl : ctgUrls) {
            // TODO: system field?
            this.createAndStoreClassItem(doRegistryEntry, "ObjectInstance",
                    new String[][] { { "url", ctgUrl }, { "resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT } });
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param conclusions
     */
    public void parseConclusions(Item study, String conclusions) {
        if (conclusions.equalsIgnoreCase("n/a")) {
            study.setAttributeIfNotNull("primaryOutcome", ConverterCVT.NOT_APPLICABLE);
        } else {
            study.setAttributeIfNotNull("primaryOutcome", conclusions);
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param conditionsStr
     * @throws Exception
     */
    public void parseConditions(Item study, String conditionsStr) throws Exception {
        // TODO: match values with CT codes/ICD Codes
        if (!ConverterUtils.isBlankOrNull(conditionsStr)) {
            String[] conditionsList = conditionsStr.split(",");
            for (String conditionStr : conditionsList) {
                this.createAndStoreClassItem(study, "StudyCondition",
                        new String[][] { { "originalValue", WordUtils.capitalizeFully(conditionStr, ' ', '-') } });
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param studyYearsStr
     * @throws Exception
     */
    public void parseStudyYears(Item study, String studyYearsStr, String studyPeriodStr) throws Exception {
        // TODO: studyPeriodStr is unused, usually more precise than study years but
        // somewhat free text

        if (!ConverterUtils.isBlankOrNull(studyYearsStr)) {
            Matcher mStudyYears = P_STUDY_YEARS.matcher(studyYearsStr);
            if (mStudyYears.matches()) {
                String startYear = mStudyYears.group(1);
                // TODO: for now setting start date as first day of year
                study.setAttribute("startDate", startYear + "-01-01");

                String endYear = mStudyYears.group(2);
                if (!endYear.equalsIgnoreCase(BiolinccConverter.STUDY_YEAR_ONGOING)) { // TODO: ongoing should be a
                                                                                       // valid value too
                    // TODO: for now setting end date as last day of year
                    study.setAttribute("endDate", endYear + "-12-31");
                }
            } else {
                this.writeLog("Failed to find study years in string: " + studyYearsStr);
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param specificConsentRestrictions
     * @param commercialUseDataRestrictions
     * @param dataRestrictionsBasedOnAreaOfResearch
     * @param irbApprovalRequiredForData
     * @param studyOpenDateData
     * @throws Exception
     */
    public void parseClinicalDatasets(Item study, String specificConsentRestrictions,
            String commercialUseDataRestrictions,
            String dataRestrictionsBasedOnAreaOfResearch,
            String irbApprovalRequiredForData, String studyOpenDateData) throws Exception {

        // Publication year for IPD DO
        String publicationYear = null;
        if (!ConverterUtils.isBlankOrNull(studyOpenDateData)) {
            LocalDate publicationDate = this.parseDate(studyOpenDateData, ConverterUtils.P_DATE_M_D_Y_TIME);
            try {
                publicationYear = Integer.toString(publicationDate.getYear());
            } catch (Exception e) {
                this.writeLog("Failed to parse 'Study Open Date (Data)' date: " + studyOpenDateData);
            }
        }

        /* IPD DataObject */
        // Note: Last updated date is on the BioLINCC website but not the data exported
        // TODO: access details?
        // TODO: access URL?
        // TODO: lang code en by default?
        // TODO: managingOrg NHLBI?
        Item ipdDO = this.createAndStoreClassItem(study, "DataObject",
                new String[][] { { "title", ConverterCVT.O_TYPE_IPD },
                        { "objectClass", ConverterCVT.O_CLASS_DATASET },
                        { "type", ConverterCVT.O_TYPE_IPD },
                        { "accessType", ConverterCVT.O_ACCESS_TYPE_CASE_BY_CASE_DOWNLOAD },
                        { "publicationYear", publicationYear } });

        // Consent details
        StringBuilder consentDetailsSb = new StringBuilder();

        // Restrictions for commercial use
        Boolean restrictCommercial = this.parseRestriction(consentDetailsSb, commercialUseDataRestrictions,
                "commercial use");
        // Restrictions based on research project type
        Boolean restrictResearchType = this.parseRestriction(consentDetailsSb, dataRestrictionsBasedOnAreaOfResearch,
                "area of research");
        // Consent details: geographical restrictions for NIH data
        consentDetailsSb.append(
                "Geographical restrictions: NIH is prohibiting access to NIH Controlled-Access Data Repositories and associated data by institutions located in countries of concern. ");
        consentDetailsSb.append(
                "These countries include China (including Hong Kong and Macau), Russia, Iran, North Korea, Cuba, and Venezuela (NOT-OD-25-083). \n");

        // IRB approval details, See Table 3.1 here:
        // https://biolincc.nhlbi.nih.gov/media/BioLINCC_User_Guide_05Jan2026.pdf
        Boolean restrictIrb = ConverterUtils.booleanFromString(irbApprovalRequiredForData);
        consentDetailsSb.append("An Institutional Review Board from the applicant's institution must ");
        if (restrictIrb != null && restrictIrb) {
            consentDetailsSb.append("perform a review of the project and issue an approval. \n");
        } else {
            consentDetailsSb.append("declare that the research project is exempt from review. \n");
        }

        // Consent details: specific restrictions
        consentDetailsSb.append(specificConsentRestrictions);

        /* ObjDataset (IPD) */
        // TODO: deidentType?
        this.createAndStoreClassItem(ipdDO, "ObjDataset",
                new String[][] { { "restrictCommercial", ConverterUtils.booleanToString(restrictCommercial) },
                        { "restrictGeo", "True" }, // See above
                        { "restrictResearchType", ConverterUtils.booleanToString(restrictResearchType) },
                        { "consentDetails", consentDetailsSb.toString() } });
    }

    public void parseBiospecimen(Item study, String specificConsentRestrictions,
            String commercialUseSpecimenRestrictions,
            String nonGeneticUseSpecimenRestrictions, String geneticUseAreaOfResearchRestrictions,
            String geneticUseOfSpecimensAllowed, String materialTypes, String studyOpenDateSpecimens) throws Exception {

        // Publication year for Biospecimen DO
        String publicationYear = null;
        if (!ConverterUtils.isBlankOrNull(studyOpenDateSpecimens)) {
            LocalDate publicationDate = this.parseDate(studyOpenDateSpecimens, ConverterUtils.P_DATE_M_D_Y_TIME);
            try {
                publicationYear = Integer.toString(publicationDate.getYear());
            } catch (Exception e) {
                this.writeLog("Failed to parse 'Study Open Date (Specimens)' date: " + studyOpenDateSpecimens);
            }
        }

        /* Biospecimen DataObject */
        // Note: Last updated date is on the BioLINCC website but not the data exported
        // TODO: access details?
        // TODO: lang code en by default?
        // TODO: managingOrg NHLBI?
        Item biospecimenDO = this.createAndStoreClassItem(study, "DataObject",
                new String[][] { { "title", ConverterCVT.O_TYPE_BIOSPECIMEN },
                        { "objectClass", ConverterCVT.O_CLASS_DATASET },
                        { "type", ConverterCVT.O_TYPE_BIOSPECIMEN },
                        { "accessType", ConverterCVT.O_ACCESS_TYPE_CASE_BY_CASE_DOWNLOAD },
                        { "publicationYear", publicationYear } });

        /* Object Description: Biospecimen types */
        if (!ConverterUtils.isBlankOrNull(materialTypes)) {
            Item descDO = this.createAndStoreClassItem(biospecimenDO, "ObjectDescription",
                    new String[][] { { "descriptionText", "Material types: " + materialTypes } });
        }

        // Consent details
        StringBuilder consentDetailsSb = new StringBuilder();

        // Restrictions for commercial use
        Boolean restrictCommercial = this.parseRestriction(consentDetailsSb, commercialUseSpecimenRestrictions,
                "commercial use");

        // Restrictions based on research project type
        Boolean restrictResearchTypeNonGenetic = this.parseRestriction(consentDetailsSb,
                nonGeneticUseSpecimenRestrictions,
                "area of research (non-genetic)");
        Boolean restrictResearchTypeGenetic = this.parseRestriction(consentDetailsSb,
                geneticUseAreaOfResearchRestrictions,
                "area of research (genetic)");

        // Setting restrictResearchType based on non-genetic + genetic restrictions and
        // adding details to consentDetails
        Boolean restrictResearchType = null;
        if (restrictResearchTypeNonGenetic != null) {
            consentDetailsSb.append("Non-genetic use specimen restrictions based on area of use: "
                    + nonGeneticUseSpecimenRestrictions + ". \n");
            restrictResearchType = restrictResearchTypeNonGenetic;
        }
        if (restrictResearchTypeGenetic != null) {
            consentDetailsSb.append("Genetic use specimen restrictions based on area of use: "
                    + geneticUseAreaOfResearchRestrictions + ". \n");
            if (restrictResearchType == null) {
                restrictResearchType = restrictResearchTypeGenetic;
            } else {
                restrictResearchType = restrictResearchType || restrictResearchTypeGenetic;
            }
        }

        // Consent details: genetic use of specimens restrictions
        if (!ConverterUtils.isBlankOrNull(geneticUseOfSpecimensAllowed)) {
            consentDetailsSb.append("Genetic use of specimens allowed: " + geneticUseOfSpecimensAllowed + ". \n");
        }

        // Consent details: geographical restrictions for NIH data
        consentDetailsSb.append(
                "Geographical restrictions: NIH is prohibiting access to NIH Controlled-Access Data Repositories and associated data by institutions located in countries of concern. ");
        consentDetailsSb.append(
                "These countries include China (including Hong Kong and Macau), Russia, Iran, North Korea, Cuba, and Venezuela (NOT-OD-25-083). \n");

        // Consent details: specific restrictions
        consentDetailsSb.append(specificConsentRestrictions);

        /* ObjDataset (IPD) */
        // TODO: deidentType?
        this.createAndStoreClassItem(biospecimenDO, "ObjDataset",
                new String[][] { { "restrictCommercial", ConverterUtils.booleanToString(restrictCommercial) },
                        { "restrictGeo", "True" }, // See above
                        { "restrictResearchType", ConverterUtils.booleanToString(restrictResearchType) },
                        { "consentDetails", consentDetailsSb.toString() } });
    }

    public Boolean parseRestriction(StringBuilder sb, String restrictionStr, String restrictionType) {
        Boolean restrict = null;

        if (!ConverterUtils.isBlankOrNull(restrictionStr)) {
            if (restrictionStr.equalsIgnoreCase("Yes")) {
                restrict = true;
            } else if (restrictionStr.equalsIgnoreCase("No")) {
                restrict = false;
            } else if (restrictionStr.equalsIgnoreCase("Not Applicable")) {
                restrict = false;
                sb.append("Restrictions for " + restrictionType + " not applicable. \n");
            } else {
                this.writeLog("Failed to parse restriction field (" + restrictionType + ") value: " + restrictionStr);
            }
        }

        return restrict;
    }

    /**
     * TODO
     * 
     * @param study
     * @param networkStr
     * @throws Exception
     */
    public void parseNetwork(Item study, String networkStr) throws Exception {
        // TODO: contribType?
        this.createAndStoreClassItem(study, "Organisation",
                new String[][] { { "name", networkStr }, { "type", "Network" } });
    }

    /**
     * TODO
     * 
     * @param study
     * @param parentStudyContactEmail
     * @param parentStudyContactName
     * @throws Exception
     */
    public void parseStudyContact(Item study, String parentStudyContactEmail, String parentStudyContactName)
            throws Exception {
        if (!ConverterUtils.isBlankOrNull(parentStudyContactName)) {
            // Note: email is not in the model so parentStudyContactEmail is unused
            this.createAndStoreClassItem(study, "Person",
                    new String[][] { { "fullName", parentStudyContactName },
                            { "contribType", ConverterCVT.CONTRIBUTOR_TYPE_SCIENTIFIC_CONTACT } });
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param participantsStr
     */
    public void parseIEC(Item study, String participantsStr) {
        study.setAttributeIfNotNull("iec", participantsStr);
    }

    public void parsePublications(Item study, String publicationUrls, String publications) throws Exception {
        // Note: publications is unused (see wiki)
        if (!ConverterUtils.isBlankOrNull(publicationUrls)) {
            String[] urls = publicationUrls.split(", ");
            Matcher mUrl;
            String pubmedId;

            // TODO: log if doesn't match/check that it's an actual pubmed url?
            for (String url : urls) {
                mUrl = ConverterUtils.P_PUBMED_ID.matcher(url);
                if (mUrl.matches()) {
                    pubmedId = mUrl.group(1);
                    if (!ConverterUtils.isBlankOrNull(pubmedId)) {
                        this.createAndStoreClassItem(study, "Publication",
                                new String[][] { { "pubMedId", pubmedId } });
                    }
                } else {
                    this.writeLog("Failed to match PubMed ID in url: " + url);
                }
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param studyWebsiteStr
     * @throws Exception
     */
    public void parseStudyWebsite(Item study, String studyWebsiteStr) throws Exception {
        // TODO: last updated
        // TODO: managing org?
        // TODO: access type?
        if (!ConverterUtils.isBlankOrNull(studyWebsiteStr)) {
            Item websiteDO = this.createAndStoreClassItem(study, "DataObject",
                    new String[][] { { "title", "Study (or clinical trial network) website" },
                            { "objectClass", ConverterCVT.O_CLASS_TEXT },
                            { "type", ConverterCVT.O_TYPE_WEBSITE } });

            Item websiteInstance = this.createAndStoreClassItem(websiteDO, "ObjectInstance",
                    new String[][] { { "url", studyWebsiteStr },
                            { "resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT } });
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param studyTypeStr
     */
    public void parseStudyType(Item study, String studyTypeStr) {
        if (!ConverterUtils.isBlankOrNull(studyTypeStr)) {
            String type = null;

            if (studyTypeStr.equalsIgnoreCase(STUDY_TYPE_CLINICAL_TRIAL)) {
                type = ConverterCVT.TYPE_INTERVENTIONAL;
            } else if (studyTypeStr.equalsIgnoreCase(STUDY_TYPE_EPIDEMIOLOGY_STUDY)) {
                type = ConverterCVT.TYPE_OBSERVATIONAL;
            } else if (studyTypeStr.equalsIgnoreCase(STUDY_TYPE_MIXED)) { // TODO: could have a "mixed" value?
                type = ConverterCVT.UNKNOWN;
            } else if (studyTypeStr.equalsIgnoreCase(STUDY_TYPE_PROGRAM_EVALUATION)) {
                type = ConverterCVT.TYPE_OTHER;
            }

            if (type != null) {
                study.setAttribute("type", type);
            } else {
                this.writeLog("Unknown study type: " + studyTypeStr);
            }
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

    @Override
    public String cleanValue(String s, boolean strip) {
        if (strip) {
            return s.strip();
        }
        return s;
    }

    /**
     * TODO
     * 
     * @param fields
     * @return map of data file field names and their corresponding column index
     * @throws Exception
     */
    public Map<String, Integer> getHeaders(String[] fields) throws Exception {
        Map<String, Integer> fieldsToInd = new HashMap<String, Integer>();

        if (fields.length == 0) {
            throw new Exception("BioLINCC data file is empty (failed getting headers)");
        }

        for (int ind = 0; ind < fields.length; ind++) {
            if (ind == 0) {
                /*
                 * Opened file is passed to this function, so we can't change the encoding on
                 * read
                 * to handle the BOM \uFEFF leading character, we have to remove it ourselves.
                 * The character is read differently in Docker, so we match the header field
                 * name starting with a letter.
                 */
                Matcher mHeader = P_HEADER.matcher(fields[ind]);
                if (mHeader.find()) {
                    fieldsToInd.put(mHeader.group(0), ind);
                } else {
                    throw new Exception("Couldn't properly parse BioLINCC first header value: '" + fields[ind] + "'");
                }
            } else {
                fieldsToInd.put(fields[ind], ind);
            }
        }

        return fieldsToInd;
    }
}
