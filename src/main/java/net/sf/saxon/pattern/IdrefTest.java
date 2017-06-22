////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.PrependIterator;

import java.io.Serializable;

/**
 * IdrefTest is a test that cannot be represented directly in XPath or
 * XSLT patterns, but which is used internally for matching IDREF nodes: it tests
 * whether the node has the is-idref property
  *
  * @author Michael H. Kay
  */

public class IdrefTest implements PatternFinder, Serializable {

    private static IdrefTest THE_INSTANCE = new IdrefTest();

    /**
     * Get the singleton instance of this class
     */

    public static IdrefTest getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Create a IdrefTest
     */

	private IdrefTest() {}

    /**
      * Select nodes in a document using this PatternFinder.
      *
     * @param doc the document node at the root of a tree
     * @param context the dynamic evaluation context
     * @return an iterator over the selected nodes in the document.
      */

     public SequenceIterator selectNodes(DocumentInfo doc, final XPathContext context) throws XPathException {

         AxisIterator allElements = doc.iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
         MappingFunction atts = new MappingFunction() {
             public SequenceIterator map(Item item) {
                 return new PrependIterator((NodeInfo)item, ((NodeInfo)item).iterateAxis(AxisInfo.ATTRIBUTE));
             }
         };
         SequenceIterator allAttributes = new MappingIterator(allElements, atts);
         ItemMappingFunction test = new ItemMappingFunction() {
             /*@Nullable*/ public Item mapItem(Item item) {
                 if ((matches((NodeInfo)item))) {
                     return item;
                 } else {
                     return null;
                 }
             }
         };
         return new ItemMappingIterator(allAttributes, test);

     }

    /**
     * Test whether this node test is satisfied by a given node.
     * @param node the node to be matched
     * @return true if the node matches the test
     */

    private boolean matches(NodeInfo node) {
        return node.isIdref();
    }


    public String toString() {
        return "is-idref()";
    }

}

