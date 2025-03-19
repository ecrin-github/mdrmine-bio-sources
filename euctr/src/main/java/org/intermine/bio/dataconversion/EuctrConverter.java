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
import java.util.HashMap;
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
                    this.writeLog("process(): start_element name: " + xr.getLocalName());
                    if (xr.getLocalName().toLowerCase().equals("trial")) {
                        EuctrTrial trial = xm.readValue(xr, EuctrTrial.class);
                        this.parseAndStoreTrial(trial);
                    }
                    break;
                case XMLEvent.CHARACTERS:
                    this.writeLog("process(): element value (CHARACTERS):" + xr.getText());
                    break;
                case XMLEvent.ATTRIBUTE:
                    this.writeLog("process(): found attribute");
                    break;
                case XMLEvent.START_DOCUMENT:
                    this.writeLog("File encoding: " + xr.getEncoding());
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
            this.newerLastUpdate = false;
            this.existingStudy = null;
            this.currentCountry = null;

            String mainId = this.getAndCleanValue(mainInfo, "trialId");

            // TODO: create subclass of base converter with item maps and relevant methods
            Item study = createItem("Study");

            if (mainId.length() != 17) {
                this.writeLog("Unexpected length for study id: " + mainId);
            } else {
                String cleanedID = mainId.substring(0, 14);
                String countryCode = mainId.substring(15, 17);
                
                if (this.studies.containsKey(cleanedID)) {   // Adding country-specific info to existing trial
                    this.existingStudy = this.studies.get(cleanedID);
                    study = this.existingStudy;
                    this.writeLog("Add country info, trial ID: " + cleanedID);
                }
    
                this.currentTrialID = cleanedID;
                if (!ConverterUtils.isNullOrEmptyOrBlank(countryCode)) {
                    this.currentCountry = this.getCountryFromField("isoAlpha2", countryCode);
                    if (this.currentCountry == null) {
                        this.writeLog("Couldn't find country from country code: " + countryCode);
                    }
                }
    
                /* EUCTR trial ID */
                String trialUrl = this.getAndCleanValue(mainInfo, "url");
                // TODO: also add ID with suffix?
                this.createAndStoreStudyIdentifier(study, this.currentTrialID, ConverterCVT.ID_TYPE_TRIAL_REGISTRY, trialUrl);
                
                // TODO: make title object
                /* Study title (need to get it before registry entry DO) */
                String publicTitle = this.getAndCleanValue(mainInfo, "publicTitle");
                String scientificTitle = this.getAndCleanValue(mainInfo, "scientificTitle");
                String scientificAcronym = this.getAndCleanValue(mainInfo, "scientificAcronym");
                this.parseTitles(study, publicTitle, scientificTitle, scientificAcronym);
    
                /* WHO universal trial number */
                String trialUtrn = this.getAndCleanValue(mainInfo, "utrn");
                // TODO: identifier type?
                this.createAndStoreStudyIdentifier(study, trialUtrn, null, null);
    
                // Unused, name of registry, seems to always be EUCTR
                String regName = this.getAndCleanValue(mainInfo, "regName");
    
                // "Date on which this record was first entered in the EudraCT database" (from trials-full.txt dat format)
                String dateRegistrationStr = this.getAndCleanValue(mainInfo, "dateRegistration");
                LocalDate dateRegistration = this.parseDate(dateRegistrationStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
    
                /* Trial registry entry DO */
                this.createAndStoreRegistryEntryDO(study, dateRegistration, trialUrl);
    
                String primarySponsor = this.getAndCleanValue(mainInfo, "primarySponsor");
                this.parsePrimarySponsor(study, primarySponsor);
    
                // Unused, always empty
                String acronym = this.getAndCleanValue(mainInfo, "acronym");
    
                /* Date enrolment (start date) */
                String dateEnrolmentStr = this.getAndCleanValue(mainInfo, "dateEnrolment");
                LocalDate dateEnrolment = this.parseDate(dateEnrolmentStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
                this.setStudyStartDate(study, dateEnrolment);
                study.setAttributeIfNotNull("testField1", "EUCTR_" + dateEnrolment != null ? dateEnrolment.toString() : null);
    
                // "Date trial authorised"
                String typeEnrolment = this.getAndCleanValue(mainInfo, "typeEnrolment");
                study.setAttributeIfNotNull("testField2", "EUCTR_" + typeEnrolment);
    
                // TODO: unused? appropriate for study enrolment?
                String targetSize = this.getAndCleanValue(mainInfo, "targetSize");
                study.setAttributeIfNotNull("testField3", "EUCTR_" + targetSize);
    
                // "Not Recruiting" or "Authorised-recruitment may be ongoing or finished" or NA
                String recruitmentStatus = this.getAndCleanValue(mainInfo, "recruitmentStatus");
                study.setAttributeIfNotNull("testField4", "EUCTR_" + recruitmentStatus);
    
                // "Interventional clinical trial of medicinal product"
                String studyType = this.getAndCleanValue(mainInfo, "studyType");
                study.setAttributeIfNotNull("testField5", "EUCTR_" + studyType);
    
                String studyDesign = this.getAndCleanValue(mainInfo, "studyDesign");
                study.setAttributeIfNotNull("testField6", "EUCTR_" + studyDesign);
    
    
                /*
                List<EuctrContact> contacts = trial.getContacts();
                if (contacts.size() > 0) {
                    this.createAndStoreClassItem(study, "StudyPeople", 
                        new String[][]{{"personFullName", contacts.get(0).getFirstname()}});
                }
    
                List<String> countries = trial.getCountries();
                if (countries.size() > 0) {
                    this.createAndStoreClassItem(study, "StudyCountry", 
                        new String[][]{{"countryName", countries.get(0)}});
                }
    
                EuctrCriteria criteria = trial.getCriteria();
                String ic = criteria.getInclusionCriteria();
                study.setAttributeIfNotNull("iec", ic);*/
    
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
                        }
                    }
                }
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