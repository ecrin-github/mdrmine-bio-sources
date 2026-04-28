package org.intermine.bio.dataconversion;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.metadata.ReferenceDescriptor;
import org.intermine.xml.full.Item;

import java.io.Reader;
import java.time.LocalDate;
import java.util.*;

public abstract class CacheConverter extends BaseConverter {

    protected Item existingStudy; // Indicates if currently parsing an existing study (if not null)
    protected boolean newerLastUpdate; // When parsing existing EUCTR study, true if last update date more recent than
                                       // current one
    protected Item currentCountry; // When parsing existing EUCTR study, country associated with country code

    /* Saving all items for later modification and storing at the end */
    // Warning: map variable names need to match collection names in model (TODO: be more precise)

    // Cache of studies, key is primary identifier (not Item or DB id)
    protected Map<String, Item> studies = new HashMap<String, Item>();
    protected Set<Item> studiesWithNoID = new HashSet<Item>();
    // Study-related classes
    protected Map<String, Set<Item>> studyConditions = new HashMap<String, Set<Item>>();
    protected Map<String, Item> allConditions = new HashMap<String, Item>();
    protected Map<String, Set<Item>> interventions = new HashMap<String, Set<Item>>();
    protected Map<CompositeKey, Item> allInterventions = new HashMap<CompositeKey, Item>();
    protected Map<String, Set<Item>> studyCountries = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> countries = new HashMap<String, Set<Item>>();  // TODO
    protected Map<String, Set<Item>> studyFeatures = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> studyIdentifiers = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> studySites = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> publications = new HashMap<String, Set<Item>>();
    // SOs
    protected Map<String, Set<Item>> resultsSummaries = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> protocols = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> statisticalAnalysisPlans = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> informedConsentForms = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> ethicsApprovalNotifications = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> individualParticipantData = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> biosamples = new HashMap<String, Set<Item>>();
    // SO-related maps
    protected Map<String, Set<Item>> datasets = new HashMap<String, Set<Item>>();
    // Common to Study and SOs (key can be study id or SO id)
    protected Map<String, Set<Item>> organisations = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> people = new HashMap<String, Set<Item>>();
    protected Map<String, Set<Item>> relationships = new HashMap<String, Set<Item>>();

    public CacheConverter(ItemWriter writer, Model model, String dataSourceName,
            String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
        this.initObjectStore();
    }

    /**
     * Method called by InterMine
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /* Opened BufferedReader is passed as argument (from FileConverterTask.execute()) */
        this.startLogging(this.dataSourceName);
        this.loadCountries();

        // Parsing in subclass
        this.parseData(reader);

        this.storeCountries();
        this.storeAllItems();
        this.stopLogging(); // TODO: ideally should be in same method as startLogging()
        /* BufferedReader is closed in FileConverterTask.execute() */
    }

    /**
     * TODO
     */
    protected abstract void parseData(Reader reader) throws Exception;

    /**
     * 
     */
    public boolean existingStudy() {
        return this.existingStudy != null;
    }

    /**
     * TODO
     * 
     * @param study
     * @param startDate
     */
    public void setStudyStartDate(Item study, LocalDate startDate) {
        if (startDate != null) {
            boolean setDate = false;
            if (!this.existingStudy()) { // If not parsing an already existing study
                setDate = true;
            } else { // Checking if the parsed start date is later than the already set one (if it
                     // exists)
                String existingDateStr = ConverterUtils.getAttrValue(study, "startDate");
                if (!ConverterUtils.isBlankOrNull(existingDateStr)
                        && startDate.compareTo(ConverterUtils.getDateFromString(existingDateStr, null)) > 0) {
                    setDate = true;
                }
            }
            if (setDate) {
                study.setAttributeIfNotNull("startDate", startDate.toString());
            }
        }
    }

    /**
     * TODO
     * 
     * @param study
     * @param endDate
     */
    public void setStudyEndDate(Item study, LocalDate endDate) {
        if (endDate != null) {
            boolean setDate = false;
            if (!this.existingStudy()) { // If not parsing an already existing study
                setDate = true;
            } else { // Checking if the parsed end date is later than the already set one (if it
                     // exists)
                String existingDateStr = ConverterUtils.getAttrValue(study, "endDate");
                if (!ConverterUtils.isBlankOrNull(existingDateStr)
                        && endDate.compareTo(ConverterUtils.getDateFromString(existingDateStr, null)) > 0) {
                    setDate = true;
                }
            }
            if (setDate) {
                study.setAttributeIfNotNull("endDate", endDate.toString());
            }
        }
    }

    /**
     * TODO
     */
    public Item linkStudyToStudyCondition(Item study, String term, String meddraCode, String meshCode, String meshTreeNumber) throws Exception {
        Item studyCondition = null;

        term = ConverterUtils.normaliseCondition(term);

        if (!ConverterUtils.isBlankOrNull(term)) {
            if (this.allConditions.containsKey(term)) {
                studyCondition = this.allConditions.get(term);
            } else { // Create StudyCondition
                studyCondition = this.createClassItem(study, "StudyCondition",
                    new String[][] { { "term", term }, { "meddraCode", meddraCode }, 
                                     { "meshCode", meshCode }, { "meshTreeNumber", meshTreeNumber } });
    
                // Add to all StudyConditions map
                this.allConditions.put(term, studyCondition);
            }
    
            if (studyCondition != null && !this.studyHasItemStored(study, studyCondition)) {
                // Add StudyCondition to collection in Study
                this.handleReferencesAndCollections(study, studyCondition);
                // Storing in cache, even if the StudyCondition already existed, because Studies and its linked items can be removed from item maps later
                this.storeClassItem(study, studyCondition);
            }
        }

        return studyCondition;
    }

    /**
     * TODO
     */
    public Item linkStudyToIntervention(Item study, String type, String name, String meshCode, String meshTreeNumber) throws Exception {
        Item intervention = null;

        // Type should already be more or less normalised
        name = ConverterUtils.normaliseIntervention(name);

        if (!ConverterUtils.isBlankOrNull(type) || !ConverterUtils.isBlankOrNull(name)) {
            CompositeKey key = new CompositeKey(type, name);
    
            if (this.allInterventions.containsKey(key)) {
                intervention = this.allInterventions.get(key);
            } else { // Create Intervention
                intervention = this.createClassItem(study, "Intervention",
                    new String[][] { { "type", type }, { "name", name }, 
                                     { "meshCode", meshCode }, { "meshTreeNumber", meshTreeNumber } });
    
                // Add to all interventions map
                this.allInterventions.put(key, intervention);
            }
    
            if (intervention != null && !this.studyHasItemStored(study, intervention)) {
                // Add Intervention to collection in Study
                this.handleReferencesAndCollections(study, intervention);
                // Storing in cache, even if the Intervention already existed, because Studies and its linked items can be removed from item maps later
                this.storeClassItem(study, intervention);
            }
        }

        return intervention;
    }

    /**
     * TODO
     * 
     * Method could be optimised with identifier/name studycountry maps
     */
    public Item getOrCreateStudyCountry(Item study, String countryName) throws Exception {
        Item studyCountry = null;

        if (!ConverterUtils.isBlankOrNull(countryName)) {
            Item country = this.getCountry(countryName);

            if (this.studyCountries.containsKey(study.getIdentifier())) {
                Set<Item> studyCountries = this.studyCountries.get(study.getIdentifier());
                
                if (country != null) {
                    for (Item sc: studyCountries) {
                        String countryReference = ConverterUtils.getRefId(sc, "country");
                        // If country item was not null we only need to check for country item equality (not country name)
                        if (countryReference != null && countryReference == country.getIdentifier()) {
                            studyCountry = sc;
                            break;
                        }
                    }
                } else {
                    for (Item sc: studyCountries) {
                        String countryNameToTest = ConverterUtils.getAttrValue(sc, "countryName");
                        if (!ConverterUtils.isBlankOrNull(countryNameToTest) && countryName.equalsIgnoreCase(countryNameToTest)) {
                            studyCountry = sc;
                            break;
                        }
                    }
                }
            }

            // Create StudyCountry
            if (studyCountry == null) {
                studyCountry = createAndStoreStudyCountry(study, country, countryName, null, null, null, null);
            }
        }

        return studyCountry;
    }

    /**
     * TODO
     */
    public boolean studyHasItemStored(Item parentItem, Item item) throws Exception {
        boolean hasItemStored = false;

        Map<String, Set<Item>> itemMap = this.getItemMapOfItem(parentItem, item);
        String parentId = parentItem.getIdentifier();
        if (itemMap != null && itemMap.containsKey(parentId) && itemMap.get(parentId).contains(item)) {
            hasItemStored = true;
        }

        return hasItemStored;
    }

    /**
     * TODO
     * 
     * @param field name of field for comparison to find the item
     * @param value value for comparison to find the item, should be unique!
     */
    public <T> Item getItemFromItemMap(Item parentItem, Map<String, Set<Item>> itemMap, String field, T value) {
        Item searchedItem = null;

        String parentId = parentItem.getIdentifier();
        if (itemMap.containsKey(parentId)) {
            Set<Item> items = itemMap.get(parentId);
            for (Item item : items) {
                if (value.equals(ConverterUtils.getAttrValue(item, field))) {
                    searchedItem = item;
                    break;
                }
            }
        }

        return searchedItem;
    }

    /**
     * TODO
     * 
     * @throws Exception
     */
    public void storeAllItems() throws Exception {
        List<Map<String, Set<Item>>> itemMaps = Arrays.asList(
                this.studyConditions, this.studyCountries, this.studyFeatures, this.studyIdentifiers, 
                this.publications, this.studySites, this.interventions, this.resultsSummaries,
                this.protocols, this.statisticalAnalysisPlans, this.informedConsentForms, 
                this.ethicsApprovalNotifications, this.individualParticipantData, this.biosamples,  
                this.organisations, this.people, this.relationships, this.datasets);

        this.writeLog("Storing all items");

        // Used to check for duplicates (right now, useful for Studies and StudyConditions)
        HashSet<String> seenIds;

        for (Map<String, Set<Item>> itemMap : itemMaps) {
            seenIds = new HashSet<String>(); // New set for each map, to avoid constructing a huge set with all item IDs
            for (Set<Item> items: itemMap.values()) {
                for (Item item: items) {
                    if (!seenIds.contains(item.getIdentifier())) {
                        store(item);
                        seenIds.add(item.getIdentifier());
                    }
                }
            }
        }

        seenIds = new HashSet<String>(); // For studies

        // TODO: check for duplicates? (as in, with the various studies IDs) and don't store them
        for (Item study : this.studies.values()) {
            if (!seenIds.contains(study.getIdentifier())) {
                store(study);
                seenIds.add(study.getIdentifier());
            }
        }
        // Adding studies with no ID
        for (Item study : this.studiesWithNoID) {
            if (!seenIds.contains(study.getIdentifier())) {
                store(study);
                seenIds.add(study.getIdentifier());
            }
        }

        this.clearMaps();
    }

    /**
     * TODO
     */
    public void saveToItemMap(Item mainClassItem, Map<String, Set<Item>> itemMap, Item itemToAdd) {
        String mainClassItemId = mainClassItem.getIdentifier();
        Set<Item> items;

        if (!itemMap.containsKey(mainClassItemId)) {
            itemMap.put(mainClassItemId, new HashSet<Item>());
        }

        items = itemMap.get(mainClassItemId);
        items.add(itemToAdd);
    }

    public void clearMaps() {
        this.studies = null;
        this.studiesWithNoID = null;
        this.studyConditions = null;
        this.allConditions = null;
        this.studyCountries = null;
        this.studyFeatures = null;
        this.studyIdentifiers = null;
        this.publications = null;
        this.studySites = null;
        this.interventions = null;
        this.allInterventions = null;
        this.resultsSummaries = null;
        this.protocols = null;
        this.statisticalAnalysisPlans = null;
        this.informedConsentForms = null;
        this.ethicsApprovalNotifications = null;
        this.individualParticipantData = null;
        this.biosamples = null;
        this.relationships = null;
        this.datasets = null;
        this.organisations = null;
        this.people = null;
    }

    /**
     * TODO
     * Does not work for studies with no ID
     */
    public void removeStudyAndLinkedItems(String mainTrialID) throws Exception {
        // Maps where key is or can be study ID (minus objects and study conditions and interventions (different handling))
        List<Map<String, Set<Item>>> studyMaps = Arrays.asList(
                this.studyCountries, this.studyFeatures, this.studyIdentifiers,
                this.publications, this.studySites, this.organisations, 
                this.people, this.relationships);
        
        // StudyObject maps
        List<Map<String, Set<Item>>> studyObjectMaps = Arrays.asList(
                this.resultsSummaries, this.protocols, this.statisticalAnalysisPlans,
                this.informedConsentForms, this.ethicsApprovalNotifications, 
                this.individualParticipantData, this.biosamples);

        // Maps where item have a collection of studies, and therefore must be handled differently
        List<Map<String, Set<Item>>> specialMaps = Arrays.asList(
                this.studyConditions, this.interventions);

        // Maps where key is or can be object ID
        List<Map<String, Set<Item>>> objectMaps = Arrays.asList(
                this.organisations, this.people, this.relationships, this.datasets);

        Item study = this.studies.get(mainTrialID);
        String studyId = study.getIdentifier();

        // Removing items linked to study
        for (Map<String, Set<Item>> itemMap : studyMaps) {
            itemMap.remove(studyId);
        }

        // Maps where need to remove study in the item's collection as well
        for (Map<String, Set<Item>> itemMap : specialMaps) {
            Set<Item> itemCollectionInStudy = itemMap.getOrDefault(studyId, null);
            if (itemCollectionInStudy != null) {
                for (Item item : itemCollectionInStudy) {
                    this.removeItemFromCollection(item, study);
                }
                itemMap.remove(studyId);
            }
        }

        // Removing objects linked to study to remove, and constructing a set of object ids
        // to remove items linked to these objects
        Set<String> objectIds = new HashSet<String>();
        for (Map<String, Set<Item>> soMap : studyObjectMaps) {
            Set<Item> objects = soMap.get(studyId);
            if (objects != null) {
                for (Item object : objects) {
                    objectIds.add(object.getIdentifier());
                }
                soMap.remove(studyId);  // Removing objects linked to study
            }
        }

        // Removing items linked to objects
        for (Map<String, Set<Item>> itemMap : objectMaps) {
            for (String objectId : objectIds) {
                itemMap.remove(objectId);
            }
        }

        // Removing study from studies map
        Item removedStudy = this.studies.remove(mainTrialID);
        if (removedStudy == null) {
            this.writeLog("Attempted to remove study, but failed, main trial ID: " + mainTrialID);
        } else {
            this.writeLog("Removed study, main trial ID: " + mainTrialID);
        }
    }

    @Override
    public Item createAndStoreClassItem(Item mainClassItem, String className, String[][] kv) throws Exception {
        Item item = this.createClassItem(mainClassItem, className, kv);

        if (item != null) {
            this.storeClassItem(mainClassItem, item);
        } else {
            this.writeLog("Failed to create item of class " + className + ", attributes: " + kv);
        }

        return item;
    }

    /**
     * TODO
     */
    public Map<String, Set<Item>> getItemMapOfItem(Item mainClassItem, Item item) throws Exception {
        // Get item map name from reference
        ReferenceDescriptor rd = this.getReferenceDescriptorInItemAOfItemB(mainClassItem, item);

        if (rd != null) {
            String mapName = rd.getName();
            return (Map<String, Set<Item>>) CacheConverter.class.getDeclaredField(mapName).get(this);
        }

        return null;
    }

    /**
     * TODO
     */
    public void storeClassItem(Item mainClassItem, Item item) throws Exception {
        Map<String, Set<Item>> itemMap = this.getItemMapOfItem(mainClassItem, item);

        if (itemMap != null) {
            this.saveToItemMap(mainClassItem, itemMap, item);
        } else if (this.getClassDescriptor(mainClassItem).getSimpleName().equalsIgnoreCase("study")
            && this.getClassDescriptor(item).getSimpleName().equalsIgnoreCase("country")) {
            // TODO: temporary solution?
            itemMap = (Map<String, Set<Item>>) CacheConverter.class
                .getDeclaredField("countries").get(this);
            this.saveToItemMap(mainClassItem, itemMap, item);
        } else {
            this.writeLog("Failed to save item to map (couldn't find map or collection from "
                        + this.getClassDescriptor(mainClassItem).getSimpleName() + " item and "
                        + this.getClassDescriptor(item).getSimpleName() + " item");
        }
    }
}
