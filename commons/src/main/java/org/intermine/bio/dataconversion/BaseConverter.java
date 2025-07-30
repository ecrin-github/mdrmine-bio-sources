package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024-2025 MDRMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.text.WordUtils;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.CollectionDescriptor;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.model.bio.Country;
import org.intermine.model.bio.Study;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.util.PropertiesUtil;
import org.intermine.xml.full.Item;



/**
 * Class with utility functions for converter classes
 * @author
 */
public abstract class BaseConverter extends BioFileConverter
{
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss.SSS");

    private String logDir = "";
    private Writer logWriter = null;
    protected String currentTrialID = null;
    protected ObjectStore os = null;

    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
                             String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence) {
        super(writer, model, dataSourceName, dataSetTitle, licence);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence, boolean storeOntology) {
        super(writer, model, dataSourceName, dataSetTitle, licence, storeOntology);
        this.initObjectStore();
    }
    
    public BaseConverter(ItemWriter writer, Model model) {
        super(writer, model);
        this.initObjectStore();
    }

    /**
     * TODO
     */
    public void initObjectStore() {
        try {
            String alias = "osw.production";
            Properties intermineProps = PropertiesUtil.getProperties();
            Properties noPrefixProps = PropertiesUtil.stripStart(alias, intermineProps);
            String osAlias = noPrefixProps.getProperty("os");
    
            this.os = ObjectStoreFactory.getObjectStore(osAlias);
        } catch(Exception e) {
            // TODO
            ;
        }
    }

    /**
     * TODO
     */
    public Item createAndStoreStudyIdentifier(Item study, String id, String identifierType, String identifierLink) throws Exception {
        Item studyIdentifier = null;

        if (!ConverterUtils.isNullOrEmptyOrBlank(id)) {
            this.createAndStoreClassItem(study, "StudyIdentifier", 
                new String[][]{{"identifierValue", id}, {"identifierType", identifierType},
                                {"identifierLink", identifierLink}});
        }

        return studyIdentifier;
    }

    /**
     * TODO
     * 
     * 2-letter ISO code
     */
    public Country getCountryFromField(String field, String value) throws Exception {
        Country c = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(value)) {
            ClassDescriptor countryCD = this.getModel().getClassDescriptorByName("Country");
            if (countryCD == null) {
                throw new RuntimeException("This model does not contain a Country class");
            }
            
            Query q = new Query();
            QueryClass countryClass = new QueryClass(countryCD.getType());
            q.addFrom(countryClass);
            q.addToSelect(countryClass);
            
            QueryField qf = new QueryField(countryClass, field);
            q.setConstraint(new SimpleConstraint(qf, ConstraintOp.EQUALS, new QueryValue(value)));
            
            Results res = this.os.execute(q);
            
            Iterator<?> resIter = res.iterator();
            try {
                ResultsRow<?> rr = (ResultsRow<?>) resIter.next();
                c = (Country) rr.get(0);
            } catch (NoSuchElementException e) {
                this.writeLog("getCountryFromField(): couldn't find country with field \"" + field + "\" and value \"" + value + "\"");
            }
        }
        return c;
    }

    /**
     * TODO
     * 
     */
    public HashMap<String, Map<String, String>> getExistingStudyIDs() throws Exception {
        HashMap<String, Map<String, String>> idsMap = new HashMap<String, Map<String, String>>();

        ClassDescriptor cdStudy = this.getModel().getClassDescriptorByName("Study");
        if (cdStudy == null) {
            throw new RuntimeException("This model does not contain a Study class");
        }

        Query q = new Query();
        QueryClass qcStudy = new QueryClass(cdStudy.getType());

        QueryField qfCtisID = new QueryField(qcStudy, "primaryIdentifier");
        QueryField qfNctID = new QueryField(qcStudy, "nctID");
        QueryField qfEuctrID = new QueryField(qcStudy, "euctrID");

        q.addFrom(qcStudy);
        q.addToSelect(qfCtisID);
        q.addToSelect(qfNctID);
        q.addToSelect(qfEuctrID);
        
        Results res = this.os.execute(q);
        Iterator<?> resIter = res.iterator();

        while (resIter.hasNext()) {
            ResultsRow<?> rr = (ResultsRow<?>) resIter.next();

            String ctisID = (String) rr.get(0);
            String nctID = (String) rr.get(1);
            String euctrID = (String) rr.get(2);

            // Note: keys need to match Study class field names
            Map<String, String> nonEmptyIDs = new HashMap<String, String>();
            if (!ConverterUtils.isNullOrEmptyOrBlank(ctisID)) {
                nonEmptyIDs.put("primaryIdentifier", ctisID);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(nctID)) {
                nonEmptyIDs.put("nctID", nctID);
            }
            if (!ConverterUtils.isNullOrEmptyOrBlank(euctrID)) {
                nonEmptyIDs.put("euctrID", euctrID);
            }

            // Adding all combinations of entries in idsMap (1 per ID)
            for (String id: nonEmptyIDs.values()) {
                idsMap.put(id, nonEmptyIDs);
            }
        }

        return idsMap;
    }

    /**
     * Instantiate logger by creating log file and writer.
     * This sets the logWriter instance attribute.
     */
    public void startLogging(String suffix) throws Exception {
        if (!this.logDir.equals("")) {
            String current_timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                
            Path logDir = Paths.get(this.logDir);
            if (!Files.exists(logDir)) Files.createDirectories(logDir);

            Path logFile = Paths.get(logDir.toString(), current_timestamp + "_" + suffix + ".log");
            this.logWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile.toString()), "utf-8"));
        } else {
            throw new Exception("Log folder not specified");
        }
    }

    /**
     * Close opened log writer.
     */
    public void stopLogging() throws IOException {
        if (this.logWriter != null) {
            this.logWriter.close();
        }
    }

    /**
     * Write to WHO log file with timestamp.
     * 
     * @param text the log text
     */
    public void writeLog(String text) {
        try {
            if (this.logWriter != null) {
                if (this.currentTrialID != null) {
                    this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + this.currentTrialID + " - " + text + "\n");
                    this.logWriter.flush();
                } else {
                    // TODO: temp, modify it to still log but without id?
                    this.logWriter.write(LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " - " + text + "\n");
                    this.logWriter.flush();
                    // System.out.println("WHO - Trial ID null (cannot write logs)");
                }
            } else {
                System.out.println("WHO - Log writer is null (cannot write logs)");
            }
        } catch(IOException e) {
            System.out.println("WHO - Couldn't write to log file");
        }
    }

    /**
     * Set logDir from the corresponding source property in project.xml.
     * Method called by InterMine.
     * 
     * @param logDir the path to the directory where the log file will be created
     */
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    /**
     * Create and store item (instance) of a class. Works for all classes except the Study class.
     * 
     * @param mainClassItem the already created item of the main class to reference (Study)
     * @param className the name of the class to create an item of
     * @param kv array of field name - field value pairs to set class item attribute values
     * @return the created item
     */
    public Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item item = this.createClassItem(mainClassItem, className, kv);
        if (item != null) {
            store(item);
        }
        return item;
    }

    /**
     * Create item (instance) of a class. Works for all classes except the Study class.
     * 
     * @param mainClassItem the already created item of the main class to reference (Study)
     * @param className the name of the class to create an item of
     * @param kv array of field name - field value pairs to set class item attribute values
     * @return the created item
     */
    public Item createClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item classItem = createItem(className);

        // Set class values from fieldName - value pairs passed as argument
        for (int j = 0; j < kv.length; j++) {
            if (kv[j].length != 2) {
                throw new Exception("Key value tuple is not of length == 2");
            }
            classItem.setAttributeIfNotNull(kv[j][0], kv[j][1]);
        }

        CollectionDescriptor collD = null;
        String collectionClassName = mainClassItem.getClassName();
        // Get reference field of class (either "study" or "linkedStudy" for data objects)
        ReferenceDescriptor referenceD = this.getReferenceDescriptorOfClass(className);

        // Get collection field of class of mainClassItem if no reference
        if (referenceD != null) {
            // Get reverse reference field of class (=collection field, e.g. "studyFeatures")
            String reverseReferenceName = referenceD.getReverseReferenceFieldName();
            
            mainClassItem.addToCollection(reverseReferenceName, classItem);
            String referenceName = referenceD.getName();
            classItem.setReference(referenceName, mainClassItem);
        } else {
            collD = this.getCollectionDescriptorOfClassFromItem(className, collectionClassName);

            if (collD != null) {
                // Get reverse reference field of class (=collection field, e.g. "people")
                String reverseReferenceName = collD.getReverseReferenceFieldName();

                mainClassItem.addToCollection(reverseReferenceName, classItem);
                String collectionName = collD.getName();
                classItem.addToCollection(collectionName, mainClassItem);
            } else {
                this.writeLog("createAndStoreClassItem(): Failed to find a known referenceName (study or dataobject) in class "
                             + className + " or collections of " + collectionClassName + " items");
            }
        }

        return classItem;
    }

    /**
     * TODO
     * only for classes that have 1 non-cv ref
     */
    public ReferenceDescriptor getReferenceDescriptorOfClass(String className) {
        ReferenceDescriptor rd = null;

        // Get class descriptor to get reference field
        ClassDescriptor cd = this.getModel().getClassDescriptorByName(className);
        ReferenceDescriptor[] rdArr = cd.getReferenceDescriptors().toArray(ReferenceDescriptor[]::new);

        // Find index of reference to non-CV class
        String refName = null;
        int i = 0;
        boolean found = false;
        while (!found && i < rdArr.length) {
            refName = rdArr[i].getName();

            if (refName.equalsIgnoreCase("study") || refName.equalsIgnoreCase("dataObject") || refName.equalsIgnoreCase("linkedStudy")) {
                found = true;
                rd = rdArr[i];
            }

            i++;
        }

        return rd;
    }

    public CollectionDescriptor getCollectionDescriptorOfClassFromItem(String className, String collectionClassName) {
        CollectionDescriptor collD = null;

        // Get class descriptor
        ClassDescriptor cd = this.getModel().getClassDescriptorByName(className);
        CollectionDescriptor[] collDArr = cd.getCollectionDescriptors().toArray(CollectionDescriptor[]::new);
        
        // Find index of collection with class name collectionClassName
        String collName = null;
        int i = 0;
        boolean found = false;
        while (!found && i < collDArr.length) {
            collName = this.getReferencedClassName(collDArr[i]);

            if (collName.equalsIgnoreCase(collectionClassName)) {
                found = true;
                collD = collDArr[i];
            }

            i++;
        }

        return collD;
    }

    /**
     * TODO
     * ReferenceDescriptor.getReferencedClassName but without package name (org.intermine.model.bio.Study -> Study)
     * @param rd
     * @return
     */
    public String getReferencedClassName(ReferenceDescriptor rd) {
        String[] splitRD = rd.getReferencedClassName().split("\\.");
        return splitRD[splitRD.length-1];
    }

    /**
     * TODO
     * Get the reverse reference field name of a class with a single reference to a study or DO item
     * e.g. studyFeatures (collection name) from StudyFeature (class name)
     */
    public String getReverseReferenceNameOfClass(String className) {
        String revRefFN = null;

        ReferenceDescriptor rd = this.getReferenceDescriptorOfClass(className);
        if (rd != null) {
            revRefFN = rd.getReverseReferenceFieldName();
        }

        return revRefFN;
    }

    /**
     * TODO
     * Get the reverse collection field name of a class
     * 
     */
    public String getReverseCollectionNameOfClass(String className, String collectionClassName) {
        String revRefFN = null;

        CollectionDescriptor collD = this.getCollectionDescriptorOfClassFromItem(className, collectionClassName);
        if (collD != null) {
            revRefFN = collD.getReverseReferenceFieldName();
        }

        return revRefFN;
    }

    /**
     * TODO
     */
    public Item createAndStoreObjectDate(Item dataObject, LocalDate date, String dateType) throws Exception {
        Item objectDate = null;

        objectDate = this.createAndStoreClassItem(dataObject, "ObjectDate", 
            new String[][]{{"dateType", dateType}, {"startDate", date != null ? date.toString() : null}});

        return objectDate;
    }

    /*
     * TODO
     */
    public Item createAndStoreStudyCountry(Item study, String countryStr, String status, 
        String plannedEnrolment, LocalDate cadDate, LocalDate ecdDate) throws Exception {

        Item studyCountry = this.createAndStoreClassItem(study, "StudyCountry", 
            new String[][]{{"countryName", WordUtils.capitalizeFully(countryStr, ' ', '-')},
                            {"status", status}, {"plannedEnrolment", plannedEnrolment},
                            {"compAuthorityDecisionDate", cadDate != null ? cadDate.toString() : null},
                            {"ethicsCommitteeDecisionDate", ecdDate != null ? ecdDate.toString() : null}});

        Country c = this.getCountryFromField("name", countryStr);
        if (c != null) {
            // TODO: figure out a way to make this work
            // studyCountry.setReference("country", String.valueOf(c.getId()));
            ;
        } else {
            this.writeLog("Couldn't match country string \"" + countryStr + "\" with an existing country");
        }

        return studyCountry;
    }

    /**
     * TODO
     */
    public LocalDate parseDate(String dateStr, DateTimeFormatter df) {
        LocalDate date = null;
        if (!ConverterUtils.isNullOrEmptyOrBlank(dateStr)) {
            if (df != null) {
                date = ConverterUtils.getDateFromString(dateStr, df);
            }
            if (date == null) { // ISO format
                date = ConverterUtils.getDateFromString(dateStr, null);
                if (date == null) {   // d(d)/m(m)/yyyy
                    date = ConverterUtils.getDateFromString(dateStr, ConverterUtils.P_DATE_D_M_Y_SLASHES);
                    if (date == null) {   // dd month(word) yyyy
                        date = ConverterUtils.getDateFromString(dateStr, ConverterUtils.P_DATE_D_MWORD_Y_SPACES);
                        if (date == null) {
                            this.writeLog("parseDate(): couldn't parse date: " + dateStr);
                        }
                    }
                }
            }
        }

        return date;
    }

    /**
     * Clean a value according to the subclass' logic. Note: method should be static but Java does not allow abstract + static
     * 
     * @param s the value to clean
     * @param strip boolean indicating whether to strip the string of any leading/trailing whitespace
     * @return the cleaned value
     * @see #unescapeHtml()
     * @see #removeQuotes()
     */
    public abstract String cleanValue(String s, boolean strip);
}
