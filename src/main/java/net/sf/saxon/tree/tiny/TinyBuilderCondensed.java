////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;

/**
 * Variant of the TinyBuilder to create a tiny tree in which multiple text nodes or attribute
 * nodes sharing the same string value economize on space by only holding the value once.
 */
public class TinyBuilderCondensed extends TinyBuilder {

    public TinyBuilderCondensed(PipelineConfiguration pipe) {
        super(pipe);
    }

    // Keep a map from the hashcode of the string (calculated within this class) to the list
    // of node numbers of text nodes having that hashcode

    // Note from the specification of CharSequence:
    // "It is therefore inappropriate to use arbitrary <tt>CharSequence</tt> instances as elements in a set or as keys in
    // a map." We therefore take special care over the design of the map.
    // We rely on the fact that all Saxon implementations of CharSequence have a hashCode() that is compatible with String.
    // And we don't use equals() unless both CharSequences are of the same kind.

    public IntHashMap<int[]> textValues = new IntHashMap<int[]>(100);


    public void endElement() throws XPathException {
        // When ending an element, consider whether the just-completed text node can be commoned-up with
        // any other text nodes. (Don't bother if its more than 256 chars, as it's then likely to be unique)
        // We do this at endElement() time because we need to make sure that adjacent text nodes are concatenated first.
        TinyTree tree = getTree();
        int n = tree.numberOfNodes-1;
        if (tree.nodeKind[n] == Type.TEXT && tree.depth[n] == getCurrentDepth() && (tree.beta[n] - tree.alpha[n] <= 256)) {
            CharSequence chars = TinyTextImpl.getStringValue(tree, n);
            int hash = chars.hashCode();
            int[] nodes = textValues.get(hash);
            if (nodes != null) {
                int used = nodes[0];
                for (int i=1; i<used; i++) {
                    int nodeNr = nodes[i];
                    if (nodeNr == 0) {
                        break;
                    } else if (isEqual(chars, TinyTextImpl.getStringValue(tree, nodeNr))) {
                        // the latest text node is equal to some previous text node
                        int length = tree.alpha[n];
                        tree.alpha[n] = tree.alpha[nodeNr];
                        tree.beta[n] = tree.beta[nodeNr];
                        tree.getCharacterBuffer().setLength(length);
                        break;
                    }
                }
            } else {
                nodes = new int[4];
                nodes[0] = 1;
                textValues.put(hash, nodes);
            }
            if (nodes[0] + 1 > nodes.length) {
                int[] n2 = new int[nodes.length*2];
                System.arraycopy(nodes, 0, n2, 0, nodes[0]);
                textValues.put(hash, n2);
                nodes = n2;
            }
            nodes[nodes[0]++] = n;
        }
        super.endElement();
    }

    /**
     * For attribute nodes, the commoning-up of stored values is achieved simply by calling intern() on the
     * string value of the attribute.
     */

    public void attribute(/*@NotNull*/ NodeName nameCode, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        super.attribute(nameCode, typeCode, value.toString().intern(), locationId, properties);
    }

    /**
     * Test whether two CharSequences contain the same characters
     * @param a the first CharSequence
     * @param b the second CharSequence
     * @return true if a and b contain the same characters
     */

    private static boolean isEqual(CharSequence a, /*@NotNull*/ CharSequence b) {
        if (a.getClass() == b.getClass()) {
            return a.equals(b);
        } else {
            return a.toString().equals(b.toString());
        }
    }

}

