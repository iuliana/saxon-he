////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

/**
 * A QName triple (prefix, URI, local) with the additional ability to hold an integer fingerprint.
 * The integer fingerprint provides a fast way of checking equality. A FingerprintedQName makes sense
 * only in the context of a known NamePool, and instances must be compared only if they relate to the
 * same NamePool. The fingerprint is optional, and is used only if present.
 */
public class FingerprintedQName extends StructuredQName implements NodeName {

    private int nameCode = -1;

    public FingerprintedQName(String prefix, String uri, String localName) {
        super(prefix, uri, localName);
    }

    public FingerprintedQName(String prefix, String uri, String localName, int nameCode) {
        super(prefix, uri, localName);
        this.nameCode = nameCode;
    }

    /**
     * Make a structuredQName from a Clark name
     * @param expandedName the name in Clark notation "{uri}local" if in a namespace, or "local" otherwise.
     * The format "{}local" is also accepted for a name in no namespace.
     * @return the constructed StructuredQName
     * @throws IllegalArgumentException if the Clark name is malformed
     */

    public static FingerprintedQName fromClarkName(String expandedName) {
        String namespace;
        String localName;
        if (expandedName.charAt(0) == '{') {
            int closeBrace = expandedName.indexOf('}');
            if (closeBrace < 0) {
                throw new IllegalArgumentException("No closing '}' in Clark name");
            }
            namespace = expandedName.substring(1, closeBrace);
            if (closeBrace == expandedName.length()) {
                throw new IllegalArgumentException("Missing local part in Clark name");
            }
            localName = expandedName.substring(closeBrace + 1);
        } else {
            namespace = "";
            localName = expandedName;
        }
        return new FingerprintedQName("", namespace, localName);
    }

    /**
     * Ask whether this node name representation has a known namecode and fingerprint
     *
     * @return true if the methods getFingerprint() and getNameCode() will
     *         return a result other than -1
     */
    public boolean hasFingerprint() {
        return nameCode != -1;
    }

    /**
     * Get the fingerprint of this name if known. This method should not to any work to allocate
     * a fingerprint if none is already available
     *
     * @return the fingerprint if known; otherwise -1
     */
    public int getFingerprint() {
        return (nameCode == -1 ? -1 : nameCode & NamePool.FP_MASK);
    }

    /**
     * Get the nameCode of this name if known. This method should not to any work to allocate
     * a nameCode if none is already available
     *
     * @return the fingerprint if known; otherwise -1
     */
    public int getNameCode() {
        return nameCode;
    }

    /**
     * Set the nameCode for this QName. Note that this modifies the FingerprintedQName object
     * and makes it unusable with a different NamePool.
     * @param nameCode the nameCode associated with this QName by the NamePool
     */

    public void setNameCode(int nameCode) {
        this.nameCode = nameCode;
    }

    /**
     * Allocate a nameCode from the NamePool (if none has already been allocated).
     * Note that this modifies the FingerprintedQName object and makes it unusable with a different NamePool.
     * @param pool the namePool
     * @return the allocated name code (or the existing namecode if there already was one)
     */

    public int allocateNameCode(NamePool pool) {
        if (nameCode == -1) {
            nameCode = pool.allocate(getPrefix(), getURI(), getLocalPart());
        }
        return nameCode;
    }

    /*
     * Compare two names for equality
     */

    public boolean equals(/*@NotNull*/ Object other) {
        if (other instanceof NodeName) {
            if (nameCode != -1 && ((NodeName)other).hasFingerprint()) {
                return getFingerprint() == ((NodeName)other).getFingerprint();
            } else {
                return getLocalPart().equals(((NodeName)other).getLocalPart()) &&
                        isInSameNamespace((NodeName)other);
            }
        } else {
            return false;
        }
    }

    public boolean isIdentical(IdentityComparable other) {
        if(other instanceof NodeName) {
            return this.equals(other) && this.getPrefix().equals(((NodeName)other).getPrefix());
        } else {
            return false;
        }
    }
}
