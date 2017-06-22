////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

/**
 * An implementation of NodeName that encapsulates an integer namecode and a reference to the NamePool from which
 * it was allocated.
 */
public class CodedName implements NodeName {

    private int nameCode;
    private NamePool pool;

    public CodedName(int nameCode, NamePool pool) {
        this.nameCode = nameCode;
        this.pool = pool;
    }

    /**
     * Get the prefix of the QName.
     *
     * @return the prefix. Returns the empty string if the name is unprefixed.
     */
    public String getPrefix() {
        return pool.getPrefix(nameCode);
    }

    /**
     * Get the namespace URI of the QName.
     *
     * @return the URI. Returns the empty string to represent the no-namespace
     */
    public String getURI() {
        return pool.getURI(nameCode);
    }

    /**
     * Get the local part of the QName
     *
     * @return the local part of the QName
     */
    public String getLocalPart() {
        return pool.getLocalName(nameCode);
    }

    /**
     * Get the display name, that is the lexical QName in the form [prefix:]local-part
     *
     * @return the lexical QName
     */
    public String getDisplayName() {
        return pool.getDisplayName(nameCode);
    }

    /**
     * Get the name in the form of a StructuredQName
     *
     * @return the name in the form of a StructuredQName
     */
    public StructuredQName getStructuredQName() {
        return pool.getStructuredQName(nameCode);
    }

    /**
     * Test whether this name is in the same namespace as another name
     *
     * @return true if the two names are in the same namespace
     */
    public boolean isInSameNamespace(/*@NotNull*/ NodeName other) {
        return getURI().equals(other.getURI());
    }

    /**
     * Test whether this name is in a given namespace
     *
     * @param ns the namespace to be tested against
     * @return true if the name is in the specified namespace
     */
    public boolean isInNamespace(String ns) {
        return getURI().equals(ns);
    }

    /**
     * Get a {@link net.sf.saxon.om.NamespaceBinding} whose (prefix, uri) pair are the prefix and URI of this
     * node name
     *
     * @return the corresponding NamespaceBinding
     */

    public NamespaceBinding getNamespaceBinding() {
        return pool.getNamespaceBinding(nameCode);
    }


    /**
     * Ask whether this node name representation has a known namecode and fingerprint
     *
     * @return true if the methods getFingerprint() and getNameCode() will
     *         return a result other than -1
     */
    public boolean hasFingerprint() {
        return true;
    }

    /**
     * Get the fingerprint of this name if known. This method should not to any work to allocate
     * a fingerprint if none is already available
     *
     * @return the fingerprint if known; otherwise -1
     */
    public int getFingerprint() {
        return nameCode & NamePool.FP_MASK;
    }

    /**
     * Get the nameCode of this name if known. This method should not to any work to allocate
     * a nameCode if none is already available
     *
     * @return the nameCode if known; otherwise -1
     */
    public int getNameCode() {
        return nameCode;
    }

    /**
     * Get the nameCode of this name, allocating a new code from the namepool if necessary
     *
     * @param namePool the NamePool used to allocate the name
     * @return a nameCode for this name, newly allocated if necessary
     */
    public int allocateNameCode(NamePool namePool) {
        return nameCode;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return StructuredQName.computeHashCode(getURI(), getLocalPart());
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NodeName) {
            NodeName n = (NodeName)obj;
            if (n.hasFingerprint()) {
                return getFingerprint() == n.getFingerprint();
            } else {
                return n.getLocalPart().equals(getLocalPart()) && n.isInNamespace(getURI());
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

