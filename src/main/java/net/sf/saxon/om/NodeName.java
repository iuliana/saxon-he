////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

/**
 * This interface represents a node name. Actually it represents any QName, but it is intended for use
 * as an element or attribute name. Various implementations are available.
 *
 * <p>An important requirement of an implementation of this interface is that the hashCode() and
 * equals() methods are implemented correctly, so that any two node names compare equal if and only
 * if the local name and namespace URI parts are equal under Unicode codepoint comparison. To ensure this,
 * the hashCode must be computed using an algorithm equivalent to that used by the implementation class
 * {@link FingerprintedQName}</p>
 */

public interface NodeName extends IdentityComparable{

    /**
     * Get the prefix of the QName.
     * @return the prefix. Returns the empty string if the name is unprefixed.
     */

    String getPrefix();

    /**
     * Get the namespace URI of the QName.
     * @return the URI. Returns the empty string to represent the no-namespace
     */

    String getURI();

    /**
     * Get the local part of the QName
     * @return the local part of the QName
     */

    String getLocalPart();

    /**
     * Get the display name, that is the lexical QName in the form [prefix:]local-part
     * @return the lexical QName
     */

    String getDisplayName();

    /**
     * Get the name in the form of a StructuredQName
     * @return the name in the form of a StructuredQName
     */

    StructuredQName getStructuredQName();

    /**
     * Test whether this name is in the same namespace as another name
     * @param other the other name
     * @return true if the two names are in the same namespace
     */

    boolean isInSameNamespace(NodeName other);

    /**
     * Test whether this name is in a given namespace
     * @param ns the namespace to be tested against
     * @return true if the name is in the specified namespace
     */

    boolean isInNamespace(String ns);

    /**
     * Get a {@link NamespaceBinding} whose (prefix, uri) pair are the prefix and URI of this
     * node name
     * @return the corresponding NamespaceBinding
     */

    NamespaceBinding getNamespaceBinding();

    /**
     * Ask whether this node name representation has a known namecode and fingerprint
     * @return true if the methods getFingerprint() and getNameCode() will
     * return a result other than -1
     */

    boolean hasFingerprint();

    /**
     * Get the fingerprint of this name if known. This method should not to any work to allocate
     * a fingerprint if none is already available
     * @return the fingerprint if known; otherwise -1
     */

    int getFingerprint();

    /**
     * Get the nameCode of this name if known. This method should not to any work to allocate
     * a nameCode if none is already available
     * @return the nameCode if known; otherwise -1
     */

    int getNameCode();

    /**
     * Get the nameCode of this name, allocating a new code from the namepool if necessary
     * @param namePool the NamePool used to allocate the name
     * @return a nameCode for this name, newly allocated if necessary
     */

    int allocateNameCode(NamePool namePool);

}
