////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.NamespaceIterator;
import net.sf.saxon.type.Type;

import java.util.Iterator;

/**
 * A NamespaceResolver that resolves namespace prefixes by reference to a node in a document for which
 * those namespaces are in-scope.
 */
public class InscopeNamespaceResolver implements NamespaceResolver {

    private NodeInfo node;

    /**
     * Create a NamespaceResolver that resolves according to the in-scope namespaces
     * of a given node
     * @param node the given node. Must not be null.
     * If this is an element node, the resolver uses the in-scope namespaces
     * of that element. If it is a document node, the only in-scope namespace is the XML namespace. If it
     * is any other kind of node, then if the node has a parent element, the resolver uses the in-scope
     * namespaces of the parent element, otherwise ithe only in-scope namespace is the XML namespace.
     */

    public InscopeNamespaceResolver(NodeInfo node) {
        int kind = node.getNodeKind();
        if (kind == Type.ELEMENT || kind == Type.DOCUMENT) {
            this.node = node;
        } else {
            NodeInfo parent = node.getParent();
            this.node = (parent == null ? node : parent);
        }
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     * @param prefix the namespace prefix. May be the zero-length string, indicating
     * that there is no prefix. This indicates either the default namespace or the
     * null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     * prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     * The "null namespace" is represented by the pseudo-URI "".
    */

    /*@Nullable*/ public String getURIForPrefix(String prefix, boolean useDefault) {
        if (prefix.length()==0 && !useDefault) {
            return NamespaceConstant.NULL;
        }
        AxisIterator iter = node.iterateAxis(AxisInfo.NAMESPACE);
        while (true) {
            NodeInfo node = iter.next();
            if (node == null) {
                break;
            }
            if (node.getLocalPart().equals(prefix)) {
                return node.getStringValue();
            }
        }
        return (prefix.length()==0 ? NamespaceConstant.NULL : null);
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    public Iterator<String> iteratePrefixes() {
        return new Iterator<String>() {
            int phase = 0;
            Iterator<NamespaceBinding> iter = NamespaceIterator.iterateNamespaces(node);

            public boolean hasNext() {
                if (iter.hasNext()) {
                    return true;
                } else if (phase == 0) {
                    phase = 1;
                    return true;
                } else {
                    return false;
                }
            }

            public String next() {
                if (phase == 1) {
                    phase = 2;
                    return "xml";
                } else {
                    return iter.next().getPrefix();
                }
            }

            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        };
    }

    /**
     * Get the node on which this namespace resolver is based
     * @return the node on which this namespace resolver is based
     */

    public NodeInfo getNode() {
        return node;
    }
}

