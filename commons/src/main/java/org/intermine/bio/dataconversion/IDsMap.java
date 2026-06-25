package org.intermine.bio.dataconversion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IDsMap {
    private static IDsMap instance = null;
    // Key: any unique study ID, value: set of studies IDsHandler
    private Map<String, Set<IDsHandler>> idsMap = new HashMap<String, Set<IDsHandler>>();

    private IDsMap() {
        IDsMap.instance = this;
    }

    public static IDsMap getIDsMap() {
        if (IDsMap.instance == null) {
            IDsMap.instance = new IDsMap();
        }

        return IDsMap.instance;
    }

    public Set<IDsHandler> get(ID id) {
        if (id != null) {
            return this.get(id.getId());
        }
        return null;
    }

    public Set<IDsHandler> get(String id) {
        return this.idsMap.getOrDefault(id, null);
    }

    public boolean add(ID id, IDsHandler idsH) {
        boolean added = false;

        if (id != null && idsH != null) {
            String idStr = id.getId();
            if (!ConverterUtils.isBlankOrNull(idStr)) {
                if (this.idsMap.containsKey(idStr)) {
                    added = this.idsMap.get(idStr).add(idsH);
                } else {
                    Set<IDsHandler> idsHSet = new HashSet<IDsHandler>();
                    this.idsMap.put(idStr, idsHSet);
                    added = idsHSet.add(idsH);

                }
            }
        }

        return added;
    }

    public boolean remove(ID id, IDsHandler idsH) {
        boolean removed = false;

        if (id != null && idsH != null) {
            Set<IDsHandler> idsHSet = this.get(id);
            if (idsHSet != null) {
                // Removing IDsHandler in Set
                removed = idsHSet.remove(idsH);

                // Removing map entry completely if it was the last corresponding to this ID
                if (idsHSet.size() == 0) {
                    this.idsMap.remove(id.getId());
                }
            }
        }

        return removed;
    }

    public void remove(String id) {
        Set<IDsHandler> idsH = this.get(id);
    }

    public boolean containsId(ID id) {
        if (id != null) {
            return this.containsId(id.getId());
        }
        return false;
    }

    public boolean containsId(String id) {
        return this.idsMap.containsKey(id);
    }

    public int size() {
        return this.idsMap.size();
    }
}
