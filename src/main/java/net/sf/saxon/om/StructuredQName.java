////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.Whitespace;

import javax.xml.namespace.QName;

/**
 * This class provides an economical representation of a QName triple (prefix, URI, and localname).
 * The value is stored internally as a character array containing the concatenation of URI, localname,
 * and prefix (in that order) with two integers giving the start positions of the localname and prefix.
 *
 * <p><i>Instances of this class are immutable.</i></p>
 */

public class StructuredQName {

    private char[] content;
    private int localNameStart;
    private int prefixStart;

    /**
     * Construct a StructuredQName from a prefix, URI, and local name. This method performs no validation.
     * @param prefix The prefix. Use an empty string to represent the null prefix.
     * @param uri The namespace URI. Use an empty string or null to represent the no-namespace
     * @param localName The local part of the name
     */

    public StructuredQName(String prefix, /*@Nullable*/ String uri, String localName) {
        if (uri == null) {
            uri = "";
        }
        int plen = prefix.length();
        int ulen = uri.length();
        int llen = localName.length();
        localNameStart = ulen;
        prefixStart = ulen + llen;
        content = new char[ulen + llen + plen];
        uri.getChars(0, ulen, content, 0);
        localName.getChars(0, llen, content, ulen);
        prefix.getChars(0, plen, content, ulen+llen);
    }

    /**
     * Make a structuredQName from a Clark name
     * @param expandedName the name in Clark notation "{uri}local" if in a namespace, or "local" otherwise.
     * The format "{}local" is also accepted for a name in no namespace. The EQName syntax (Q{uri}local) is
     * also accepted.
     * @return the constructed StructuredQName
     * @throws IllegalArgumentException if the Clark name is malformed
     */

    public static StructuredQName fromClarkName(String expandedName) {
        String namespace;
        String localName;
        if (expandedName.startsWith("Q{")) {
            expandedName = expandedName.substring(1);
        }
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
        return new StructuredQName("", namespace, localName);
    }

    /**
     * Make a structured QName from a lexical QName, using a supplied NamespaceResolver to
     * resolve the prefix
     *
     * @param lexicalName the QName as a lexical name (prefix:local)
     * @param useDefault set to true if an absent prefix implies use of the default namespace;
     * set to false if an absent prefix implies no namespace
     * @param allowEQName true if the EQName syntax Q{uri}local is acceptable
     * @param checker NameChecker to be used to check conformance against XML 1.0 or 1.1 lexical rules
     * @param resolver NamespaceResolver used to look up a URI for the prefix
     * @return the StructuredQName object corresponding to this lexical QName
     * @throws XPathException if the namespace prefix is not in scope or if the value is lexically
     * invalid. Error code FONS0004 is set if the namespace prefix has not been declared; error
     * code FOCA0002 is set if the name is lexically invalid.
     */

    public static StructuredQName fromLexicalQName(CharSequence lexicalName, boolean useDefault,
                                                   boolean allowEQName, NameChecker checker, NamespaceResolver resolver)
    throws XPathException {
        lexicalName = Whitespace.trimWhitespace(lexicalName);
        if (allowEQName && lexicalName.length() >= 4 && lexicalName.charAt(0) == 'Q' && lexicalName.charAt(1) == '{') {
            String name = lexicalName.toString();
            int endBrace = name.indexOf('}');
            if (endBrace < 0) {
                throw new XPathException("Invalid EQName: closing brace not found");
            } else if (endBrace == name.length() - 1) {
                throw new XPathException("Invalid EQName: local part is missing");
            }
            String uri = name.substring(2, endBrace);
            String local = name.substring(endBrace + 1);
            if (!checker.isValidNCName(local)) {
                throw new XPathException("Invalid EQName: local part is not a valid NCName");
            }
            return new StructuredQName("", uri, local);
        }
        try {
            String[] parts = checker.getQNameParts(lexicalName);
            String uri = resolver.getURIForPrefix(parts[0], useDefault);
            if (uri == null) {
                XPathException de = new XPathException("Namespace prefix '" + parts[0] + "' has not been declared");
                de.setErrorCode("FONS0004");
                throw de;
            }
            return new StructuredQName(parts[0], uri, parts[1]);
        } catch (QNameException e) {
            XPathException de = new XPathException(e.getMessage());
            de.setErrorCode("FOCA0002");
            throw de;
        }
    }

    /**
     * Make a structured QName from a lexical QName, using a supplied NamespaceResolver to
     * resolve the prefix
     *
     * @param eqName the QName as an EQname (Q{uri}local), or an unqualified local name. The format is not checked.
     * @return the StructuredQName object corresponding to this lexical QName
     * @throws IllegalArgumentException if the eqName is obviously invalid
     */

    public static StructuredQName fromEQName(CharSequence eqName) {
        eqName = Whitespace.trimWhitespace(eqName);
        if (eqName.length() >= 4 && eqName.charAt(0) == 'Q' && eqName.charAt(1) == '{') {
            String name = eqName.toString();
            int endBrace = name.indexOf('}');
            if (endBrace < 0) {
                throw new IllegalArgumentException("Invalid EQName: closing brace not found");
            } else if (endBrace == name.length() - 1) {
                throw new IllegalArgumentException("Invalid EQName: local part is missing");
            }
            String uri = name.substring(2, endBrace);
            String local = name.substring(endBrace + 1);
            return new StructuredQName("", uri, local);
        } else {
            return new StructuredQName("", "", eqName.toString());
        }
    }


    /**
     * Get the prefix of the QName.
     * @return the prefix. Returns the empty string if the name is unprefixed.
     */

    public String getPrefix() {
        return new String(content, prefixStart, content.length - prefixStart);
    }

    /**
     * Get the namespace URI of the QName.
     * @return the URI. Returns the empty string to represent the no-namespace
     */

    public String getURI() {
        if (localNameStart == 0) {
            return "";
        }
        return new String(content, 0, localNameStart);
    }

    /**
     * Get the local part of the QName
     * @return the local part of the QName
     */

    public String getLocalPart() {
        return new String(content, localNameStart, prefixStart - localNameStart);
    }

    /**
     * Get the display name, that is the lexical QName in the form [prefix:]local-part
     * @return the lexical QName
     */

    public String getDisplayName() {
        if (prefixStart == content.length) {
            return getLocalPart();
        } else {
            FastStringBuffer buff = new FastStringBuffer(content.length - localNameStart + 1);
            buff.append(content, prefixStart, content.length - prefixStart);
            buff.append(':');
            buff.append(content, localNameStart, prefixStart - localNameStart);
            return buff.toString();
        }
    }

    /**
     * Get the name as a StructuredQName (which it already is; but this satisfies the NodeName interface)
     */

    public StructuredQName getStructuredQName() {
        return this;
    }

    /**
     * Get the expanded QName in Clark format, that is "{uri}local" if it is in a namespace, or just "local"
     * otherwise.
     * @return the QName in Clark notation
     */

    public String getClarkName() {
        FastStringBuffer buff = new FastStringBuffer(content.length - prefixStart + 2);
        if (localNameStart > 0) {
            buff.append('{');
            buff.append(content, 0, localNameStart);
            buff.append('}');
        }
        buff.append(content, localNameStart, prefixStart - localNameStart);
        return buff.toString();
    }

    /**
     * Get the expanded QName as an EQName, that is "Q{uri}local" for a name in a namespace,
     * or "Q{}local" otherwise
     * @return the QName in EQName notation
     */

    public String getEQName() {
        FastStringBuffer buff = new FastStringBuffer(content.length - prefixStart + 2);
        buff.append("Q{");
        if (localNameStart > 0) {
            buff.append(content, 0, localNameStart);
        }
        buff.append('}');
        buff.append(content, localNameStart, prefixStart - localNameStart);
        return buff.toString();
    }

    /**
     * The toString() method displays the QName as a lexical QName, that is prefix:local
     * @return the lexical QName
     */

    public String toString() {
        return getDisplayName();
    }

    /**
     * Compare two StructuredQName values for equality. This compares the URI and local name parts,
     * excluding any prefix
     */

    public boolean equals(Object other) {
        if (other instanceof StructuredQName) {
            StructuredQName sq2 = (StructuredQName)other;
            if (localNameStart != sq2.localNameStart || prefixStart != sq2.prefixStart) {
                return false;
            }
            for (int i=prefixStart-1; i>=0; i--) {
                // compare from the end of the local name to maximize chance of finding a difference quickly
                if (content[i] != sq2.content[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a hashcode to reflect the equals() method
     * @return a hashcode based on the URI and local part only, ignoring the prefix.
     */

    public int hashCode() {
        int h = 0x8004a00b;
        h ^= prefixStart;
        h ^= localNameStart;
        for (int i=prefixStart-1; i>=0; i--) {
            h ^= (content[i] << (i&0x1f));
        }
        return h;
    }

    /**
     * Expose the hashCode algorithm so that other implementations of QNames can construct a compatible hashcode
     * @param uri the namespace URI
     * @param local the local name
     * @return a hash code computed from the URI and local name
     */

    public static int computeHashCode(CharSequence uri, CharSequence local) {
        int h = 0x8004a00b;
        int localLen = local.length();
        int uriLen = uri.length();
        int totalLen = localLen + uriLen;
        h ^= totalLen;
        h ^= uriLen;
        for (int i=0; i<uriLen; i++) {
            h ^= (uri.charAt(i) << (i&0x1f));
        }
        for (int i=0, j=uriLen; i<localLen; i++, j++) {
            h ^= (local.charAt(i) << (j&0x1f));
        }
        return h;
    }

    /**
     * Test whether this name is in the same namespace as another name
     * @param other the other name
     * @return true if the two names are in the same namespace
     */

    public boolean isInSameNamespace(NodeName other) {
        if (this == other) {
            return true;
        }
        if (other instanceof StructuredQName) {
            StructuredQName q2 = (StructuredQName)other;
            if (localNameStart != q2.localNameStart) {
                return false;
            }
            for (int i=localNameStart-1; i>=0; i--) {
                // compare from the end of the URI to maximize chance of finding a difference quickly
                if (content[i] != q2.content[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return getURI().equals(other.getURI());
        }
    }

    /**
     * Test whether this name is in a given namespace
     * @param ns the namespace to be tested against
     * @return true if the name is in the specified namespace
     */

    public boolean isInNamespace(String ns) {
        if (ns.length()==0) {
            return localNameStart == 0;
        } else {
            return localNameStart == ns.length() && getURI().equals(ns);
        }
    }

    /**
     * Convert the StructuredQName to a javax.xml.namespace.QName
     * @return an object of class javax.xml.namespace.QName representing this qualified name
     */

    public QName toJaxpQName() {
        return new javax.xml.namespace.QName(getURI(), getLocalPart(), getPrefix());
    }

    /**
     * Get the NamespaceBinding (prefix/uri pair) corresponding to this name
     * @return a NamespaceBinding containing the prefix and URI present in this QName
     */

    public NamespaceBinding getNamespaceBinding() {
        return NamespaceBinding.makeNamespaceBinding(
                new CharSlice(content, prefixStart, content.length - prefixStart),
                new CharSlice(content, 0, localNameStart));
    }

    /**
     * Ask whether this node name representation has a known namecode and fingerprint
     *
     * @return true if the methods getFingerprint() and getNameCode() will
     *         return a result other than -1
     */
    public boolean hasFingerprint() {
        return false;
    }

    /**
     * Get the fingerprint of this name if known. This method should not to any work to allocate
     * a fingerprint if none is already available
     *
     * @return the fingerprint if known; otherwise -1
     */
    public int getFingerprint() {
        return -1;
    }

    /**
     * Get the nameCode of this name if known. This method should not to any work to allocate
     * a nameCode if none is already available
     *
     * @return the fingerprint if known; otherwise -1
     */
    public int getNameCode() {
        return -1;
    }
}

