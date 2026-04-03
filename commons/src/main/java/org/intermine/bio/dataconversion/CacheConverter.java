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
    protected List<Item> studiesWithNoID = new ArrayList<Item>();
    // Study-related classes
    protected Map<String, List<Item>> studyConditions = new HashMap<String, List<Item>>();
    protected Map<CompositeKey, Item> allConditions = new HashMap<CompositeKey, Item>();
    protected Map<String, List<Item>> studyCountries = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> countries = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyFeatures = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyIdentifiers = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> dataSources = new HashMap<String, List<Item>>();  // TODO?
    protected Map<String, List<Item>> publications = new HashMap<String, List<Item>>();
    // SOs
    protected Map<String, List<Item>> objects = new HashMap<String, List<Item>>();
    // SO-related maps
    protected Map<String, List<Item>> datasets = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> biosamples = new HashMap<String, List<Item>>();
    // Common to Study and SOs (key can be study id or DO id)
    // TODO: all items lists?
    protected Map<String, List<Item>> locations = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> organisations = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> people = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> relationships = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> titles = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> topics = new HashMap<String, List<Item>>();

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
    public Item linkStudyToStudyCondition(Item study, String term, String code, String terminology) throws Exception {
        Item studyCondition = null;

        CompositeKey k = new CompositeKey(term, code);

        if (this.allConditions.containsKey(k)) {
            studyCondition = this.allConditions.get(k);
        } else { // Create StudyCondition
            studyCondition = this.createClassItem(study, "StudyCondition",
                new String[][] { { "term", term }, { "code", code }, { "terminology", terminology } });

            // Add to map (using composite key) with all StudyConditions
            this.allConditions.put(k, studyCondition);
        }

        if (studyCondition != null) {
            // Add StudyCondition to collection in Study
            this.handleReferencesAndCollections(study, studyCondition);
            // Storing in cache, even if the StudyCondition already existed, because Studies and its linked items can be removed from item maps later
            this.storeClassItem(study, studyCondition);
        }

        return studyCondition;
    }

    /**
     * TODO
     * 
     * @param field name of field for comparison to find the item
     * @param value value for comparison to find the item, should be unique!
     */
    public <T> Item getItemFromItemMap(Item parentItem, Map<String, List<Item>> itemMap, String field, T value) {
        Item searchedItem = null;

        String parentId = parentItem.getIdentifier();
        if (itemMap.containsKey(parentId)) {
            List<Item> items = itemMap.get(parentId);
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
        List<Map<String, List<Item>>> itemMaps = Arrays.asList(
                this.studyConditions, this.studyCountries, this.studyFeatures, this.studyIdentifiers,
                this.dataSources, this.publications,
                this.locations, this.organisations, this.people, this.relationships, this.titles, this.topics,
                this.objects, this.relationships, this.datasets, this.biosamples);

        this.writeLog("Storing all items");

        // Used to check for duplicates (right now, useful for Studies and StudyConditions)
        HashSet<String> seenIds;

        for (Map<String, List<Item>> itemMap : itemMaps) {
            seenIds = new HashSet<String>(); // New set for each map, to avoid constructing a huge set with all item IDs
            for (List<Item> items : itemMap.values()) {
                for (Item item : items) {
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
    public void saveToItemMap(Item mainClassItem, Map<String, List<Item>> itemMap, Item itemToAdd) {
        String mainClassItemId = mainClassItem.getIdentifier();
        List<Item> itemList;

        if (!itemMap.containsKey(mainClassItemId)) {
            itemMap.put(mainClassItemId, new ArrayList<Item>());
        }

        itemList = itemMap.get(mainClassItemId);
        itemList.add(itemToAdd);
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
        this.dataSources = null;
        this.locations = null;
        this.organisations = null;
        this.people = null;
        this.relationships = null;
        this.titles = null;
        this.topics = null;
        this.objects = null;
        this.datasets = null;
        this.biosamples = null;
        this.relationships = null;
    }

    /**
     * TODO
     * Does not work for studies with no ID
     */
    public void removeStudyAndLinkedItems(String mainTrialID) throws Exception {
        // Maps where key is or can be study ID (minus objects and study conditions (different handling))
        List<Map<String, List<Item>>> studyMaps = Arrays.asList(
                this.studyCountries, this.studyFeatures, this.studyIdentifiers,
                this.dataSources, this.publications, this.locations, this.organisations, 
                this.people, this.relationships, this.titles, this.topics, this.relationships);

        // Maps where key is or can be object ID
        List<Map<String, List<Item>>> objectMaps = Arrays.asList(
                this.locations, this.organisations, this.people, 
                this.relationships, this.titles, this.topics, this.relationships, this.datasets, this.biosamples);

        Item study = this.studies.get(mainTrialID);
        String studyId = study.getIdentifier();

        // Removing items linked to study
        for (Map<String, List<Item>> itemMap : studyMaps) {
            itemMap.remove(studyId);
        }

        // Different handling for study conditions, need to remove study in StudyCondition's collection as well
        List<Item> studyConditions = this.studyConditions.getOrDefault(studyId, null);
        if (studyConditions != null) {
            for (Item studyCondition : studyConditions) {
                this.removeItemFromCollection(studyCondition, study);
            }
            this.studyConditions.remove(studyId);
        }

        List<Item> objects = this.objects.get(studyId);
        if (objects != null) {
            // Retrieving all object IDs
            List<String> objectIds = new ArrayList<String>();
            for (Item object : objects) {
                objectIds.add(object.getIdentifier());
            }

            // Removing items linked to objects
            for (Map<String, List<Item>> itemMap : objectMaps) {
                for (String objectId : objectIds) {
                    itemMap.remove(objectId);
                }
            }
        }

        // Removing objects
        this.objects.remove(studyId);

        // Removing study from studies map
        Item removedStudy = this.studies.remove(mainTrialID);
        // TODO: run with this
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
    public void storeClassItem(Item mainClassItem, Item item) throws Exception {
        // Get item map name from reference
        ReferenceDescriptor rd = this.getReferenceDescriptorInItemAOfItemB(mainClassItem, item);

        if (rd != null) {
            String mapName = rd.getName();

            Map<String, List<Item>> itemMap = (Map<String, List<Item>>) CacheConverter.class.getDeclaredField(mapName)
                    .get(this);
            if (itemMap != null) {
                this.saveToItemMap(mainClassItem, itemMap, item);
            } else {
                this.writeLog("Failed to save item to map (couldn't find map '" + mapName + "') from "
                        + this.getClassDescriptor(mainClassItem).getSimpleName() + " item and "
                        + this.getClassDescriptor(item).getSimpleName() + " item");
            }
        } else {
            // TODO: temporary solution?
            if (this.getClassDescriptor(mainClassItem).getSimpleName().equalsIgnoreCase("study")
                    && this.getClassDescriptor(item).getSimpleName().equalsIgnoreCase("country")) {
                Map<String, List<Item>> itemMap = (Map<String, List<Item>>) CacheConverter.class
                        .getDeclaredField("countries").get(this);
                this.saveToItemMap(mainClassItem, itemMap, item);
            } else {
                this.writeLog("Failed to find collection in "
                        + this.getClassDescriptor(mainClassItem).getSimpleName() + " class of "
                        + this.getClassDescriptor(item).getSimpleName() + " items");
            }
        }
    }
}
