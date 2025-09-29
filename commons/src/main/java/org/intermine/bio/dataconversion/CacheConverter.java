package org.intermine.bio.dataconversion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.model.bio.Country;
import org.intermine.xml.full.Item;



public abstract class CacheConverter extends BaseConverter {

    protected Item existingStudy;    // Indicates if currently parsing an existing study (if not null)
    protected boolean newerLastUpdate; // When parsing existing EUCTR study, true if last update date more recent than current one
    protected Country currentCountry; // When parsing existing EUCTR study, country associated with country code

    /* Saving all items for later modification and storing at the end  */
    // Cache of studies, key is primary identifier (not Item or DB id)
    protected Map<String, Item> studies = new HashMap<String, Item>();
    // Study-related classes
    protected Map<String, List<Item>> studyConditions = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyCountries = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyFeatures = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyICDs = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studyIdentifiers = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> studySources = new HashMap<String, List<Item>>();
    // DOs
    protected Map<String, List<Item>> objects = new HashMap<String, List<Item>>();
    // DO-related classes
    protected Map<String, List<Item>> objectDates = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> objectDescriptions = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> objectIdentifiers = new HashMap<String, List<Item>>();
    protected Map<String, List<Item>> objectInstances = new HashMap<String, List<Item>>();
    // Common to Study and DOs (key can be study id or DO id)
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
    
    public CacheConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence) {
        super(writer, model, dataSourceName, dataSetTitle, licence);
        this.initObjectStore();
    }
    
    public CacheConverter(ItemWriter writer, Model model, String dataSourceName,
    String dataSetTitle, String licence, boolean storeOntology) {
        super(writer, model, dataSourceName, dataSetTitle, licence, storeOntology);
        this.initObjectStore();
    }
    
    public CacheConverter(ItemWriter writer, Model model) {
        super(writer, model);
        this.initObjectStore();
    }

    /**
     * 
     */
    public boolean existingStudy() {
        return this.existingStudy != null;
    }

    /**
     * TODO
     * @param study
     * @param startDate
     */
    public void setStudyStartDate(Item study, LocalDate startDate) {
        if (startDate != null) {
            boolean setDate = false;
            if (!this.existingStudy()) {  // If not parsing an already existing study
                setDate = true;
            } else {    // Checking if the parsed start date is later than the already set one (if it exists)
                String existingDateStr = ConverterUtils.getValueOfItemAttribute(study, "startDate");
                if (!ConverterUtils.isNullOrEmptyOrBlank(existingDateStr)
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
     * @param study
     * @param endDate
     */
    public void setStudyEndDate(Item study, LocalDate endDate) {
        if (endDate != null) {
            boolean setDate = false;
            if (!this.existingStudy()) {  // If not parsing an already existing study
                setDate = true;
            } else {    // Checking if the parsed end date is later than the already set one (if it exists)
                String existingDateStr = ConverterUtils.getValueOfItemAttribute(study, "endDate");
                if (!ConverterUtils.isNullOrEmptyOrBlank(existingDateStr)
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
     * @param field name of field for comparison to find the item
     * @param value value for comparison to find the item, should be unique!
     */
    public Item getItemFromItemMap(Item parentItem, Map<String, List<Item>> itemMap, String field, String value) {
        Item searchedItem = null;

        String parentId = parentItem.getIdentifier();
        if (itemMap.containsKey(parentId)) {
            List<Item> items = itemMap.get(parentId);
            for (Item item: items) {
                if (value.equals(ConverterUtils.getValueOfItemAttribute(item, field))) {
                    searchedItem = item;
                    break;
                }
            }
        }

        return searchedItem;
    }

    /**
     * TODO
     * @throws Exception
     */
    public void storeAllItems() throws Exception {
        List<Map<String, List<Item>>> itemMaps = Arrays.asList(
            this.studyConditions, this.studyCountries, this.studyFeatures, this.studyICDs, this.studyIdentifiers, this.studySources,
            this.locations, this.organisations, this.people, this.relationships, this.titles, this.topics, this.objects, this.relationships,
            this.objectDates, this.objectDescriptions, this.objectIdentifiers, this.objectInstances
        );

        for (Map<String, List<Item>> itemMap: itemMaps) {
            for (List<Item> items: itemMap.values()) {
                for (Item item: items) {
                    store(item);
                }
            }
        }

        // TODO: check for duplicates and don't store them
        HashSet<String> seenIds = new HashSet<String>();
        for (Item study: this.studies.values()) {
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
        this.studyConditions = null;
        this.studyCountries = null;
        this.studyFeatures = null;
        this.studyICDs = null;
        this.studyIdentifiers = null;
        this.studySources = null;
        this.locations = null;
        this.organisations = null;
        this.people = null;
        this.relationships = null;
        this.titles = null;
        this.topics = null;
        this.objects = null;
        this.relationships = null;
        this.objectDates = null;
        this.objectDescriptions = null;
        this.objectIdentifiers = null;
        this.objectInstances = null;
    }

    /**
     * TODO
     * @param study
     */
    public void removeStudyAndLinkedItems(String mainTrialID) {
        // Maps where key is or can be study ID (-objects)
        List<Map<String, List<Item>>> studyMaps = Arrays.asList(
            this.studyConditions, this.studyCountries, this.studyFeatures, this.studyICDs, this.studyIdentifiers, this.studySources,
            this.locations, this.organisations, this.people, this.relationships, this.titles, this.topics, this.relationships
        );

        // Maps where key is or can be object ID
        List<Map<String, List<Item>>> objectMaps = Arrays.asList(
            this.objectDates, this.objectDescriptions, this.objectIdentifiers, this.objectInstances,
            this.locations, this.organisations, this.people, this.relationships, this.titles, this.topics, this.relationships
        );

        Item study = this.studies.get(mainTrialID);
        String studyId = study.getIdentifier();
        
        // Removing items linked to study
        for (Map<String, List<Item>> itemMap: studyMaps) {
            itemMap.remove(studyId);
        }
        
        List<Item> objects = this.objects.get(studyId);
        if (objects != null) {
            // Retrieving all object IDs
            List<String> objectIds = new ArrayList<String>();
            for (Item object: objects) {
                objectIds.add(object.getIdentifier());
            }
            
            // Removing items linked to objects
            for (Map<String, List<Item>> itemMap: objectMaps) {
                for (String objectId: objectIds) {
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
            // Get item map name from reference
            String mapName = this.getReverseReferenceNameOfClass(className);
            
            // Get item map name from collection
            if (mapName == null) {
                mapName = this.getReverseCollectionNameOfClass(className, mainClassItem.getClassName());
            }
            
            if (mapName != null) {
                Map<String, List<Item>> itemMap = (Map<String, List<Item>>) CacheConverter.class.getDeclaredField(mapName).get(this);
                if (itemMap != null) {
                    this.saveToItemMap(mainClassItem, itemMap, item);
                } else {
                    this.writeLog("Failed to save item to map, class name: " + className);
                }
            } else {
                this.writeLog("Failed to save item to map (couldn't find map), class name: " + className);
            }
        } else {
            this.writeLog("Failed to create item of class " + className + ", attributes: " + kv);
        }

        return item;
    }

}
