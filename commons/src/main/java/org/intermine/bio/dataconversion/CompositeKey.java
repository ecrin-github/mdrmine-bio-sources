package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2024-2026 MDRMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Objects;


public final class CompositeKey {
    private String a = null;
    private String b = null;

    public CompositeKey(String a, String b) {
        if (!ConverterUtils.isBlankOrNull(a)) {
            this.a = a.toLowerCase();
        }

        if (!ConverterUtils.isBlankOrNull(b)) {
            this.b = b.toLowerCase();
        }

        if (this.a == null && this.b == null) {
            throw new IllegalArgumentException("At least one field must be non-null");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof CompositeKey) {
            CompositeKey k = (CompositeKey) obj;
            return Objects.equals(this.a, k.a) && Objects.equals(this.b, k.b);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.a) * 31 + Objects.hashCode(this.b);    // null-safe
    }
}