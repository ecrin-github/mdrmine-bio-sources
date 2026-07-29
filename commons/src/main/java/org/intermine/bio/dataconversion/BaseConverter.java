package org.intermine.bio.dataconversion;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.text.WordUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.Study;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.ObjectStoreWriterFactory;
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
 * Base class for converter classes, contains base methods for creating and
 * storing items, and interact with the objectstore
 * 
 * @author
 *         Note: this.getModel() to access model
 */
public abstract class BaseConverter extends BioFileConverter {
    protected String dataSourceName = "";
    protected String dataSetTitle = "";
    private String logDir = "";
    protected Logger logger = null;
    protected String currentTrialID = null;
    protected ObjectStore os = null;
    protected ObjectStoreWriter osw = null;

    public BaseConverter(ItemWriter writer, Model model, String dataSourceName,
            String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
        this.dataSourceName = dataSourceName;
        this.dataSetTitle = dataSetTitle;
        this.initObjectStore();
    }

    /**
     * Clean a value according to the subclass' logic. Note: method should be static
     * but Java does not allow abstract + static
     * 
     * @param s     the value to clean
     * @param strip boolean indicating whether to strip the string of any
     *              leading/trailing whitespace
     * @return the cleaned value
     * @see #unescapeHtml()
     * @see #removeQuotes()
     */
    public abstract String cleanValue(String s, boolean strip);

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
     * Instantiate logger by creating log file and writer.
     * This sets the logWriter instance attribute.
     */
    public void startLogging(String suffix) throws Exception {
        this.logger = new Logger(logDir, suffix);
    }

    /**
     * Close opened log writer.
     */
    public void stopLogging() throws IOException {
        if (this.logger != null) {
            this.logger.stopLogging();
        } else {
            System.out.println("Attempted to stop logging on a null logger");
        }
    }

    /**
     * Write to log file with timestamp.
     * 
     * @param text the log text
     */
    public void writeLog(String text) {
        if (this.logger != null) {
            this.logger.writeLog(this.currentTrialID, text);
        } else {
            System.err.println("Logger is null (cannot write logs)");
        }
    }

    /**
     * TODO
     */
    public void initObjectStore() {
        try {
            Properties intermineProps = PropertiesUtil.getProperties();

            String alias = "osw.production";
            Properties noPrefixProps = PropertiesUtil.stripStart(alias, intermineProps);
            String osAlias = noPrefixProps.getProperty("os");

            this.os = ObjectStoreFactory.getObjectStore(osAlias);
            this.osw = ObjectStoreWriterFactory.getObjectStoreWriter(alias);
        } catch (Exception e) {
            this.writeLog(e.toString());
            System.err.println(e.toString());
        }
    }

    /**
     * Create and store item (instance) of a class. Works for all classes except the
     * Study and Country classes.
     * 
     * @param mainClassItem the already created item of the main class to reference
     *                      (Study)
     * @param className     the name of the class to create an item of
     * @param kv            array of field name - field value pairs to set class
     *                      item attribute values
     * @return the created item
     */
    public Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item item = this.createClassItem(mainClassItem, className, kv);
        this.storeClassItem(mainClassItem, item);

        return item;
    }

    /**
     * Create item (instance) of a class.
     * 
     * @param itemToReference an already created item to reference (e.g. Study)
     * @param className       the name of the class to create an item of
     * @param kv              array of field name - field value pairs to set class
     *                        item attribute values
     * @return the created item
     */
    public Item createClassItem(Item itemToReference, String className, String[][] kv) throws Exception {
        Item classItem = this.createItem(className);

        // Set class values from fieldName - value pairs passed as argument
        for (int j = 0; j < kv.length; j++) {
            if (kv[j].length != 2) {
                throw new Exception("Key value tuple is not of length == 2");
            }
            classItem.setAttributeIfNotNull(kv[j][0], kv[j][1]);
        }

        if (classItem != null) {
            this.handleReferencesAndCollections(itemToReference, classItem);
        }

        return classItem;
    }

    /**
     * TODO
     * 
     * @param className
     * @param kv
     * @return
     * @throws Exception
     */
    public Item createClassItem(String className, String[][] kv) throws Exception {
        Item classItem = this.createItem(className);

        // Set class values from fieldName - value pairs passed as argument
        for (int j = 0; j < kv.length; j++) {
            if (kv[j].length != 2) {
                throw new Exception("Key value tuple is not of length == 2");
            }
            classItem.setAttributeIfNotNull(kv[j][0], kv[j][1]);
        }

        return classItem;
    }

    /**
     * TODO
     * Note: mainClassItem is required for overloading with CacheConverter
     */
    public void storeClassItem(Item mainClassItem, Item item) throws Exception {
        if (item != null) {
            store(item);
        }
    }

    public ClassDescriptor getClassDescriptor(Item item) {
        return ConverterUtils.getClassDescriptor(this.getModel(), item);
    }

    public Set<ClassDescriptor> getAllDescriptors(Item item) {
        return ConverterUtils.getAllDescriptors(this.getModel(), item);
    }

    public boolean removeItemFromCollection(Item item, Item itemToRemove) throws Exception {
        return ConverterUtils.removeItemFromCollection(this.getModel(), item, itemToRemove);
    }

    public void handleReferencesAndCollections(Item itemA, Item itemB) throws Exception {
        ConverterUtils.handleReferencesAndCollections(this.getModel(), itemA, itemB);
    }

    public ReferenceDescriptor getReferenceDescriptorInItemAOfItemB(Item itemA, Item itemB)
            throws Exception {
        return ConverterUtils.getReferenceDescriptorInItemAOfItemB(this.getModel(), itemA, itemB);
    }

    /**
     * TODO
     * primaryIdentifier modification
     */
    public void modifyStoredStudyId(String existingID, String newID) throws Exception {
        ClassDescriptor cdStudy = this.getModel().getClassDescriptorByName("Study");
        Query q = new Query();
        QueryClass qcStudy = new QueryClass(cdStudy.getType());

        QueryField qfPrimaryID = new QueryField(qcStudy, "primaryIdentifier");

        q.addFrom(qcStudy);
        q.addToSelect(qcStudy);
        q.setConstraint(new SimpleConstraint(qfPrimaryID, ConstraintOp.EQUALS, new QueryValue(existingID)));

        Results res = this.os.execute(q);
        Iterator<?> resIter = res.iterator();

        if (resIter.hasNext()) {
            ResultsRow<?> rr = (ResultsRow<?>) resIter.next();
            Study study = (Study) rr.get(0); // Study is InterMineObject (superclass of Item)

            study.setPrimaryIdentifier(newID);

            InterMineObject studyObj = (InterMineObject) study;
            this.osw.store(studyObj);

            // TODO: check if hasnext again?

            // Database db = ((ObjectStoreWriterInterMineImpl) this.osw).getDatabase();
            // Connection con = db.getConnection();
            // con.setAutoCommit(true);

            // this.writeLog("Attempting to update stored study ID from " + existingID + "
            // to " + newID);
            // Statement stm = con.createStatement();
            // String sql3 = "UPDATE study SET primaryidentifier='" + newID
            // + "' WHERE primaryidentifier='" + existingID + "'";
            // stm.executeUpdate(sql3);
            // stm.close();
            // con.close();
        } else {
            this.writeLog("Couldn't find stored study ID to change to the new ID, tried: " + existingID);
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param id
     * @param unique
     * @param type
     * @param source
     * @return
     * @throws Exception
     */
    public Item createAndStoreStudyIdentifier(Item study, String id, String source, String type)
            throws Exception {
        Item studyIdentifier = null;

        if (!ConverterUtils.isBlankOrNull(id)) {
            this.createAndStoreClassItem(study, "StudyIdentifier",
                    new String[][] { { "value", id },
                            { "type", type },
                            { "source", source } });
        }

        return studyIdentifier;
    }

    /*
     * TODO
     */
    public Item createAndStoreStudyCountry(Item study, Item country, String countryStr, String status,
            String plannedEnrolment, LocalDate cadDate, LocalDate ecdDate) throws Exception {

        Item studyCountry = this.createAndStoreClassItem(study, "StudyCountry",
                new String[][] { { "countryName", WordUtils.capitalizeFully(countryStr, ' ', '-') },
                        { "status", status }, { "plannedEnrolment", plannedEnrolment },
                        { "compAuthorityDecisionDate", cadDate != null ? cadDate.toString() : null },
                        { "ethicsCommitteeDecisionDate", ecdDate != null ? ecdDate.toString() : null } });

        // Set references and collections between the Country and StudyCountry items and
        // store the Country item
        if (country != null) {
            this.handleReferencesAndCollections(country, studyCountry);
            this.storeClassItem(study, country); // TODO: remove? duplicate with storeCountries()?
        }

        return studyCountry;
    }
}
