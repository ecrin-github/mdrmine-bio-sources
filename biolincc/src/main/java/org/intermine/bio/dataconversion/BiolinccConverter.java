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

import org.apache.commons.text.WordUtils;

import java.io.Reader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 
 * @author
 */
public class BiolinccConverter extends BaseConverter
{
    //
    private static final String DATASET_TITLE = "BioLINCC full";
    private static final String DATA_SOURCE_NAME = "BioLINCC";

    private static final Pattern P_HEADER = Pattern.compile("\\w+.*");
    private static final Pattern P_URL_NCT_ID = Pattern.compile(".*\\/([^?]+).*");

    private static final String CTG_STUDY_BASE_URL = "https://clinicaltrials.gov/study/";
    private static final String AGE_ADULT = "Adult";
    private static final String AGE_PEDIATRIC = "Pediatric";
    private static final String AGE_ALL = "Both";


    private Map<String, Integer> fieldsToInd;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
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
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
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
        this.createAndStoreClassItem(study, "StudySource", new String[][]{{"name", DATA_SOURCE_NAME}});

        /* Study title */
        String studyName = this.getAndCleanValue(lineValues, "Study Name");
        // Study acronym
        String acronym = this.getAndCleanValue(lineValues, "Acronym");
        this.parseStudyTitle(study, studyName, acronym);

        // Study ID     Note: parsing ID after title to log title if ID is empty
        String biolinccID = this.getAndCleanValue(lineValues, "Accession Number");
        // TODO: should not be primaryIdentifier
        this.parseID(study, biolinccID);
        
        // TODO: check with other fields
        String availableResources = this.getAndCleanValue(lineValues, "Available resources");

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
        study.setAttributeIfNotNull("testField1", clinicalTrialUrls);

        /* Registry entry DOs */
        // TODO: merge trial possibly (based on clinicalTrialUrls)
        this.createAndStoreRegistryEntryDO(study, biolinccUrl, clinicalTrialUrls);

        /* Study age IC */
        String cohortType = this.getAndCleanValue(lineValues, "Cohort type");
        this.parseAge(study, cohortType);

        // TODO: use with dataset DO for ObjDataset.consentNonCommercial
        String commercialUseDataRestrictions = this.getAndCleanValue(lineValues, "Commercial use data restrictions");
        study.setAttributeIfNotNull("testField2", commercialUseDataRestrictions);
        
        // TODO: possibly same as above (for different dataset obj)?
        String commercialUseSpecimenRestrictions = this.getAndCleanValue(lineValues, "Commercial use specimen restrictions");
        study.setAttributeIfNotNull("testField3", commercialUseSpecimenRestrictions);
        
        /* Study primary outcome(s) */
        String conclusions = this.getAndCleanValue(lineValues, "Conclusions");
        this.parseConclusions(study, conclusions);
        
        /* Study conditions */
        String conditions = this.getAndCleanValue(lineValues, "Conditions");
        this.parseConditions(study, conditions);
        study.setAttributeIfNotNull("testField4", conditions);
        
        String dataRestrictionsBasedOnAreaOfResearch = this.getAndCleanValue(lineValues, "Data restrictions based on area of research");
        study.setAttributeIfNotNull("testField5", dataRestrictionsBasedOnAreaOfResearch);

        String design = this.getAndCleanValue(lineValues, "Design");
        study.setAttributeIfNotNull("testField6", design);

        String extraStudyDetails = this.getAndCleanValue(lineValues, "Extra Study Details");
        study.setAttributeIfNotNull("testField7", extraStudyDetails);

        String geneticUseAreaOfResearchRestrictions = this.getAndCleanValue(lineValues, "Genetic use area of research restrictions");
        study.setAttributeIfNotNull("testField8", geneticUseAreaOfResearchRestrictions);

        String geneticUseOfSpecimensAllowed = this.getAndCleanValue(lineValues, "Genetic use of specimens allowed?");
        study.setAttributeIfNotNull("testField9", geneticUseOfSpecimensAllowed);

        String hivStudyClassification = this.getAndCleanValue(lineValues, "HIV study classification");
        String hasSpecimens = this.getAndCleanValue(lineValues, "Has Specimens");
        String hasStudyDatasets = this.getAndCleanValue(lineValues, "Has Study Datasets");
        String irbApprovalRequiredForData = this.getAndCleanValue(lineValues, "IRB approval required for data");
        String ingestionStatus = this.getAndCleanValue(lineValues, "Ingestion Status");
        String isPublicUseDataset = this.getAndCleanValue(lineValues, "Is public use dataset");
        String materialTypes = this.getAndCleanValue(lineValues, "Material Types");
        String nhlbiDivision = this.getAndCleanValue(lineValues, "NHLBI Division");
        String network = this.getAndCleanValue(lineValues, "Network");
        String nonGeneticUseSpecimenRestrictions = this.getAndCleanValue(lineValues, "Non-genetic use specimen restrictions based on area of use");
        String parentStudyContactEmail = this.getAndCleanValue(lineValues, "Parent Study Contact Email");
        String parentStudyContactName = this.getAndCleanValue(lineValues, "Parent Study Contact Name");
        String participants = this.getAndCleanValue(lineValues, "Participants");
        String publicationUrls = this.getAndCleanValue(lineValues, "Publication urls");
        String publications = this.getAndCleanValue(lineValues, "Publications");
        String relatedStudies = this.getAndCleanValue(lineValues, "Related studies");
        String specificConsentRestrictions = this.getAndCleanValue(lineValues, "Specific Consent Restrictions");
        String studyOpenDateData = this.getAndCleanValue(lineValues, "Study Open Date (Data)");
        String studyOpenDateSpecimens = this.getAndCleanValue(lineValues, "Study Open Date (Specimens)");
        String studyWebsite = this.getAndCleanValue(lineValues, "Study Website");
        String studyYears = this.getAndCleanValue(lineValues, "Study Years");
        String studyPeriod = this.getAndCleanValue(lineValues, "Study period");
        String studyType = this.getAndCleanValue(lineValues, "Study type");

        store(study);
        this.currentTrialID = null;
    }

    /**
     * TODO
     * @param study
     * @param biolinccID
     */
    public void parseID(Item study, String biolinccID) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(biolinccID)) {
            this.currentTrialID = biolinccID;
            study.setAttributeIfNotNull("primaryIdentifier", this.currentTrialID);
        } else {
            this.writeLog("Encountered study with no ID, title: " + ConverterUtils.getValueOfItemAttribute(study, "displayTitle"));
        }
    }

    /**
     * TODO
     * Same as CTG
     * @param study
     * @param studyTitle
     */
    public void parseStudyTitle(Item study, String studyTitle, String acronym) throws Exception {
        boolean displayTitleSet = false;

        // TODO: filter out garb values if any?
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
     * @param background
     * @param objectives
     */
    public void constructBriefDescription(Item study, String background, String objectives) {
        StringBuilder sb = new StringBuilder();

        // Background
        if (!ConverterUtils.isNullOrEmptyOrBlank(background)) {
            sb.append("Background: ");
            sb.append(background);
        }

        // Objectives
        if (!ConverterUtils.isNullOrEmptyOrBlank(objectives)) {
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
     * @param study
     * @param cohortType
     */
    public void parseAge(Item study, String cohortType) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(cohortType)) {
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
            if (!ConverterUtils.isNullOrEmptyOrBlank(minAge)) {
                study.setAttributeIfNotNull("minAge", minAge);
                study.setAttributeIfNotNull("minAgeUnit", ConverterCVT.AGE_UNIT_YEARS);
            }
            
            if (!ConverterUtils.isNullOrEmptyOrBlank(maxAge)) {
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
     * @param clinicalTrialUrls
     * @return
     */
    public List<String> parseClinicalTrialUrls(String clinicalTrialUrls) {
        Set<String> parsedUrls = new HashSet<String>();

        if (!ConverterUtils.isNullOrEmptyOrBlank(clinicalTrialUrls)) {
            String[] urls = clinicalTrialUrls.split(", ");
            Matcher mUrl;
            String ctgUrl;

            for (String url: urls) {
                mUrl = P_URL_NCT_ID.matcher(url);
                if (mUrl.matches()) {
                    ctgUrl = CTG_STUDY_BASE_URL + mUrl.group(1);
                    parsedUrls.add(ctgUrl);
                } else {
                    this.writeLog("Failed to match CTG URL: " + url);
                }
            }
        }

        return new ArrayList<String>(parsedUrls);
    }

    public void createAndStoreRegistryEntryDO(Item study, String biolinccUrl, String clinicalTrialUrls) throws Exception {
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
            new String[][]{{"title", doDisplayTitle}, {"objectClass", ConverterCVT.O_CLASS_TEXT}, 
                            {"type", ConverterCVT.O_TYPE_TRIAL_REGISTRY_ENTRY}});
        
        /* Biolincc registry entry DO instance */
            this.createAndStoreClassItem(doRegistryEntry, "ObjectInstance", 
            new String[][]{{"url", biolinccUrl}, {"resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT}});
            
        /* CTG Registry entry DO instances */
        List<String> ctgUrls = this.parseClinicalTrialUrls(clinicalTrialUrls);
        for (String ctgUrl: ctgUrls) {
            // TODO: system field?
            this.createAndStoreClassItem(doRegistryEntry, "ObjectInstance", 
                new String[][]{{"url", ctgUrl}, {"resourceType", ConverterCVT.O_RESOURCE_TYPE_WEB_TEXT}});
        }
    }

    /**
     * TODO
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
     * @param study
     * @param conditionsStr
     * @throws Exception
     */
    public void parseConditions(Item study, String conditionsStr) throws Exception {
        // TODO: match values with CT codes/ICD Codes
        if (!ConverterUtils.isNullOrEmptyOrBlank(conditionsStr)) {
            String[] conditionsList = conditionsStr.split(",");
            for (String conditionStr: conditionsList) {
                this.createAndStoreClassItem(study, "StudyCondition", 
                    new String[][]{{"originalValue", WordUtils.capitalizeFully(conditionStr, ' ', '-')}});
            }
        }
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
                /* Opened file is passed to this function, so we can't change the encoding on read
                to handle the BOM \uFEFF leading character, we have to remove it ourselves.
                The character is read differently in Docker, so we match the header field name starting with a letter. */
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
