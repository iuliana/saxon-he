////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trace.Location;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;

/**
* Details about an instruction, used when reporting errors and when tracing
*/

public final class InstructionDetails implements InstructionInfo, Serializable {

    private int constructType = Location.UNCLASSIFIED;
    /*@Nullable*/ private String systemId = null;
    private int lineNumber = -1;
    private int columnNumber = -1;
    /*@Nullable*/ private StructuredQName objectName;
    private HashMap<String, Object> properties = new HashMap<String, Object>(5);

    public InstructionDetails() {}

    /**
     * Set the type of construct
     * @param type the type of contruct
     */

    public void setConstructType(int type) {
        constructType = type;
    }

    /**
     * Get the construct type
     */
    public int getConstructType() {
        return constructType;
    }

    /**
    * Set the URI of the module containing the instruction
    * @param systemId the module's URI, or null indicating unknown
    */

    public void setSystemId(/*@Nullable*/ String systemId) {
        this.systemId = systemId;
    }

    /**
    * Get the URI of the module containing the instruction
    * @return the module's URI, or null indicating unknown
    */

    /*@Nullable*/
    public String getSystemId() {
        return systemId;
    }

    /**
    * Set the line number of the instruction within the module
    * @param lineNumber the line number
    */

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
    * Get the line number of the instruction within its module
    * @return the line number
    */

    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Set a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * @param qName the name of the object, for example a function or variable name, or null to indicate
     * that it has no name
     */

    public void setObjectName(/*@Nullable*/ StructuredQName qName) {
        objectName = qName;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * @return the name of the object, or null to indicate that it has no name
     */

    /*@Nullable*/
    public StructuredQName getObjectName() {
        if (objectName != null) {
            return objectName;
        } else {
            return null;
        }
    }

    /**
     * Set a named property of the instruction
     * @param name the name of the property
     * @param value the value of the property
     */

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Get a named property of the instruction
     * @param name name of the property
     * @return the value of the named property
     */

    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     * @return an iterator over the names of the properties
     */

    public Iterator<String> getProperties() {
        return properties.keySet().iterator();
    }

    /**
    * Get the public ID of the module containing the instruction. This method
    * is provided to satisfy the SourceLocator interface. However, the public ID is
    * not maintained by Saxon, and the method always returns null
    * @return null
    */

    public String getPublicId() {
        return null;
    }

    /**
     * Set the column number
     * @param column the column number of the instruction in the source module
     */

    public void setColumnNumber(int column) {
        columnNumber = column;
    }

    /**
    * Get the column number identifying the position of the instruction.
    * @return -1 if column number is not known
    */

    public int getColumnNumber() {
        return columnNumber;
    }

    public String getSystemId(long locationId) {
        return getSystemId();
    }

    public int getLineNumber(long locationId) {
        return getLineNumber();
    }

    public int getColumnNumber(long locationId) {
        return getColumnNumber();
    }
}

