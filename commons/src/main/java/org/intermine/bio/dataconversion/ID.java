package org.intermine.bio.dataconversion;

public class ID {
    private String id;
    public String source = null;
    public String type = null;
    public boolean unique = false;

    public ID(String id, String source) {
        this(id, source, null, null);
    }

    public ID(String id, String source, String type) {
        this(id, source, type, null);
    }

    public ID(String id, String source, String type, Boolean unique) {
        this.id = id;
        this.source = source;
        this.type = type;
        this.unique = unique;
    }

    public String getId() {
        return this.id;
    }

    public String getSource() {
        return this.source;
    }

    public String getType() {
        return this.type;
    }

    public Boolean getUnique() {
        return this.unique;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj.getClass() != this.getClass()) {
            return false;
        }

        final ID other = (ID) obj;
        if (!this.id.equals(other.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("ID [");
        sb.append("id: ");
        sb.append(this.id);
        sb.append(", source: ");
        sb.append(this.source);
        sb.append(", type: ");
        sb.append(this.type);
        sb.append(", unique: ");
        sb.append(this.unique);
        sb.append("]");

        return sb.toString();
    }
}
