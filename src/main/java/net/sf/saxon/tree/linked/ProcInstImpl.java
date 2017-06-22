////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
  * ProcInstImpl is an implementation of ProcInstInfo used by the Propagator to construct
  * its trees.
  * @author Michael H. Kay
  */


class ProcInstImpl extends NodeImpl {

    String content;
    int nameCode;
    String systemId;
    int lineNumber = -1;

    public ProcInstImpl(int nameCode, String content) {
        this.nameCode = nameCode;
        this.content = content;
    }

	/**
	* Get the nameCode of the node. This is used to locate the name in the NamePool
	*/

	public int getNameCode() {
		return nameCode;
	}

    public String getStringValue() {
        return content;
    }

    /**
     * Get the typed value of this node.
     * Returns the string value, as an instance of xs:string
     */

    /*@NotNull*/ public AtomicSequence atomize() {
        return new StringValue(getStringValue());
    }
    
    public final int getNodeKind() {
        return Type.PROCESSING_INSTRUCTION;
    }

    /**
     * Set the system ID and line number
     * @param uri the system identifier
     * @param lineNumber the line number
    */

    public void setLocation(String uri, int lineNumber) {
        this.systemId = uri;
        this.lineNumber = lineNumber;
    }

    /**
     * Get the system ID for the entity containing this node.
     * @return the system identifier
    */

    public String getSystemId() {
        return systemId;
    }

    /**
    * Get the line number of the node within its source entity
    */

    public int getLineNumber() {
        return lineNumber;
    }

    /**
    * Copy this node to a given outputter
    */

    public void copy(/*@NotNull*/ Receiver out, int copyOptions, int locationId) throws XPathException {
        out.processingInstruction(getLocalPart(), content, locationId, 0);
    }

    /**
     * Rename this node
     *
     * @param newNameCode the NamePool code of the new name
     */

    public void rename(NodeName newNameCode)  {
        nameCode = getNamePool().allocate("", "", newNameCode.getLocalPart());
    }


    /**
     * Replace the string-value of this node
     *
     * @param stringValue the new string value
     */

     public void replaceStringValue(/*@NotNull*/ CharSequence stringValue) {
        content = stringValue.toString();
    }
}

