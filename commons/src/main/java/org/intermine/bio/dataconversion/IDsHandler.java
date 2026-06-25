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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.intermine.model.bio.StudyIdentifier;

/**
 * TODO
 * 
 * @author
 */
public class IDsHandler {
    public static int handlersNb = 0;
    private int id;
    public String dataSource = null;
    public String primaryIdentifier = null;
    public Set<ID> uids = new HashSet<ID>();
    public Set<ID> nonUids = new HashSet<ID>();

    public IDsHandler(String dataSource) {
        this.dataSource = dataSource;
        this.setInternalId();
    }

    public IDsHandler(String dataSource, ID id) {
        this.dataSource = dataSource;

        if (id != null) {
            this.addId(id);
            if (id.getUnique()) {
                this.primaryIdentifier = id.getId();
            }
        }

        this.setInternalId();
    }

    public IDsHandler(String dataSource, List<StudyIdentifier> ids) {
        this(dataSource, null, ids);
    }

    public IDsHandler(String dataSource, String primaryIdentifier, List<StudyIdentifier> ids) {
        this.dataSource = dataSource;
        this.primaryIdentifier = primaryIdentifier;

        if (ids != null && ids.size() > 0) {
            for (StudyIdentifier id : ids) {
                this.addId(id);
            }
        }

        this.setInternalId();
    }

    public IDsHandler(String dataSource, Set<String> ids) {
        this(dataSource, null, ids);
    }

    public IDsHandler(String dataSource, String primaryIdentifier, Set<String> ids) {
        this.dataSource = dataSource;
        this.primaryIdentifier = primaryIdentifier;

        if (ids != null) {
            for (String id : ids) {
                this.addId(id);
            }
        }

        this.setInternalId();
    }

    private void setInternalId() {
        IDsHandler.handlersNb++;
        this.id = IDsHandler.handlersNb;
    }

    public ID addId(ID id) {
        if (id != null) {
            if (id.getUnique()) {
                this.uids.add(id);
            } else {
                this.nonUids.add(id);
            }
        }
        return id;
    }

    public ID addId(String id) {
        if (!ConverterUtils.isBlankOrNull(id)) {
            ID idObj = ConverterUtils.createStudyID(id);
            return this.addId(idObj);
        }
        return null;
    }

    public String addId(StudyIdentifier id) {
        if (id != null) {
            ID idObj = new ID(id.getValue(), id.getSource(), id.getType(), id.getUnique());
            this.addId(idObj);
        }
        return null;
    }

    public Set<ID> addUids(Set<ID> uids) {
        if (uids != null) {
            this.uids.addAll(uids);
        }
        return this.uids;
    }

    public Set<ID> addNonUids(Set<ID> nonUids) {
        if (nonUids != null) {
            this.nonUids.addAll(nonUids);
        }
        return this.nonUids;
    }

    public void addIds(Set<String> ids) {
        if (ids != null) {
            for (String id : ids) {
                this.addId(id);
            }
        }
    }

    public Set<ID> getUids() {
        return this.uids;
    }

    public boolean hasUid(ID uid) {
        return this.uids.contains(uid);
    }

    public boolean hasNonUid(ID nonuid) {
        return this.nonUids.contains(nonuid);
    }

    public String removeId(String id) {
        if (!ConverterUtils.isBlankOrNull(id)) {
            ID idObj = ConverterUtils.createStudyID(id);
            if (idObj.getUnique()) {
                this.uids.remove(idObj);
            } else {
                this.nonUids.remove(idObj);
            }
        }
        return null;
    }

    // TODO: primaryIdentifier should be ID? if set here, should be added to ids set
    public String setPrimaryIdentifier(String primaryIdentifier) {
        this.primaryIdentifier = primaryIdentifier;
        return this.primaryIdentifier;
    }

    /**
     * Get any unique ID from the IDsHandler, or null if no IDs are present
     * 
     * @return the unique ID
     */
    public ID getAnyUid() {
        ID uid = null;

        if (this.uids.size() > 0) {
            uid = this.uids.iterator().next();
        }

        return uid;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj.getClass() != this.getClass()) {
            return false;
        }

        final IDsHandler other = (IDsHandler) obj;
        if (this.id != other.id) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = getClass().hashCode();
        hash = 31 * hash + this.id;
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("IDsHandler [");
        sb.append("id: ");
        sb.append(this.id);
        sb.append(", dataSource: ");
        sb.append(this.dataSource);
        sb.append(", primaryIdentifier: ");
        sb.append(this.primaryIdentifier);
        sb.append(", uids: ");
        sb.append(this.uids);
        sb.append(", nonuids: ");
        sb.append(this.nonUids);
        sb.append("]");

        return sb.toString();
    }
}
