package org.intermine.bio.dataconversion;

import java.io.File;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Completion;
import javax.print.attribute.standard.MediaSize.Other;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;

import org.apache.commons.text.WordUtils;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.Update;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.model.bio.Study;
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

    private static final Pattern P_EUCTR_ID = Pattern.compile(".*\\b\\d{4}-\\d{6}-\\d{2}\\b");
    private static final Pattern P_CTIS_ID = Pattern.compile(".*\\b\\d{4}-\\d{6}-\\d{2}-\\\\d{2}\\b");

    private Map<String, Integer> fieldsToInd;

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

    public void storeValues(String[] lineValues) throws Exception {
        Item study = createItem("Study");

        /* Trial ID */
        // TODO: don't store study if value is empty
        String trialID = this.getAndCleanValue(lineValues, "NCT Number");
        this.parseTrialID(study, trialID);
        
        /* Study title */
        String studyTitle = this.getAndCleanValue(lineValues, "Study Title");
        // Study acronym
        String acronym = this.getAndCleanValue(lineValues, "Acronym");
        this.parseStudyTitle(study, studyTitle, acronym);
        
        // TODO: not working as intended
        Item studySource = createItem("StudySource");
        studySource.setAttribute("sourceName", "CTG");
        studySource.setReference("study", study);
        store(studySource);

        // Registry trial page URL
        String studyURL = this.getAndCleanValue(lineValues, "Study URL");
        
        // TODO
        String studyStatus = this.getAndCleanValue(lineValues, "Study Status");
        this.parseStatus(study, studyStatus);

        // Unused, "Yes"/"No"
        String studyResults = this.getAndCleanValue(lineValues, "Study Results");
        study.setAttributeIfNotNull("testField3", studyResults);

        // TODO
        String conditions = this.getAndCleanValue(lineValues, "Conditions");
        this.parseConditions(study, conditions);
        study.setAttributeIfNotNull("testField4", conditions);

        // TODO
        String interventions = this.getAndCleanValue(lineValues, "Interventions");
        this.parseInterventions(study, interventions);
        study.setAttributeIfNotNull("testField5", interventions);

        // TODO
        String primaryOutcomeMeasures = this.getAndCleanValue(lineValues, "Primary Outcome Measures");
        study.setAttributeIfNotNull("testField6", primaryOutcomeMeasures);

        // TODO
        String secondaryOutcomeMeasures = this.getAndCleanValue(lineValues, "Secondary Outcome Measures");
        study.setAttributeIfNotNull("testField7", secondaryOutcomeMeasures);

        // TODO
        String otherOutcomeMeasures = this.getAndCleanValue(lineValues, "Other Outcome Measures");
        study.setAttributeIfNotNull("testField8", otherOutcomeMeasures);

        /* Study brief description */
        String briefSummary = this.getAndCleanValue(lineValues, "Brief Summary");
        this.parseBriefSummary(study, briefSummary);

        String otherIDs = this.getAndCleanValue(lineValues, "Other IDs");
        this.parseOtherIDs(study, otherIDs);
        
        /* Trial registry entry DO */

        // this.createAndStoreRegistryEntryDO(study, FIRST POSTED DATE?, studyURL);

        String sponsor = this.getAndCleanValue(lineValues, "Sponsor");
        String collaborators = this.getAndCleanValue(lineValues, "Collaborators");
        String gender = this.getAndCleanValue(lineValues, "Sex");
        String age = this.getAndCleanValue(lineValues, "Age");
        String phases = this.getAndCleanValue(lineValues, "Phases");
        String enrolment = this.getAndCleanValue(lineValues, "Enrollment");
        String funderType = this.getAndCleanValue(lineValues, "Funder Type");
        String studyType = this.getAndCleanValue(lineValues, "Study Type");
        String studyDesign = this.getAndCleanValue(lineValues, "Study Design");
        String startDateStr = this.getAndCleanValue(lineValues, "Start Date");
        String primaryCompletionDateStr = this.getAndCleanValue(lineValues, "Primary Completion Date");
        String completionDateStr = this.getAndCleanValue(lineValues, "Completion Date");
        String firstPostedStr = this.getAndCleanValue(lineValues, "First Posted");
        String resultsFirstPostedStr = this.getAndCleanValue(lineValues, "Results First Posted");
        String lastUpdatePostedStr = this.getAndCleanValue(lineValues, "Last Update Posted");
        String locations = this.getAndCleanValue(lineValues, "Locations");
        String studyDocuments = this.getAndCleanValue(lineValues, "Study Documents");

        store(study);
    }

    /**
     * TODO
     * @param study
     * @param studyTitle
     */
    public void parseTrialID(Item study, String trialID) {
        // NCT ID
        study.setAttributeIfNotNull("secondaryIdentifier", trialID);
        // study.setAttributeIfNotNull("primaryIdentifier", trialID);
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

    /**
     * TODO
     * @param study
     * @param otherIDsStr
     * @throws Exception
     */
    public void parseOtherIDs(Item study, String otherIDsStr) throws Exception {
        // example: GO42784|2021-000129-28|2023-507172-44-00
        // first id: sponsor protocol code, euctr id, ctis id
        // TODO: as secondary identifiers?
        if (!ConverterUtils.isNullOrEmptyOrBlank(otherIDsStr)) {
            String[] otherIDs = otherIDsStr.split("\\|");
            for (String otherID: otherIDs) {
                otherID = otherID.strip();

                Matcher mEuctr = CtgConverter.P_EUCTR_ID.matcher(otherID);
                if (mEuctr.matches()) {
                    // study.setAttributeIfNotNull("euctrID", otherID);
                    study.setAttributeIfNotNull("primaryIdentifier", otherID);
                } else {
                    Matcher mCtis = CtgConverter.P_CTIS_ID.matcher(otherID);
                    if (mCtis.matches()) {
                        study.setAttributeIfNotNull("ctisID", otherID);
                    } else {
                        this.createAndStoreClassItem(study, "StudyIdentifier", 
                            new String[][]{{"identifierValue", otherID}});
                    }
                }
            }
        }
    }

    public void parseStatus(Item study, String status) {
        if (!ConverterUtils.isNullOrEmptyOrBlank(status)) {
            // TODO: normalisation
            study.setAttributeIfNotNull("status", status);
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
            String[] conditions = conditionsStr.split("|");
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
                            new String[][]{{"type", tuple[0]}, {"value", tuple[1]}});
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
     * @param creationDate
     * @param url
     * @throws Exception
     */
    public void createAndStoreRegistryEntryDO(Item study, LocalDate creationDate, String url) throws Exception {
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
