////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.event.LocationProvider;
import net.sf.saxon.event.SaxonLocator;
import net.sf.saxon.expr.instruct.LocationMap;
import org.xml.sax.Locator;

import javax.xml.transform.SourceLocator;
import java.io.Serializable;

/**
 * Class to hold details of the location of an expression, of an error in a source file, etc.
 */

public class ExpressionLocation implements SaxonLocator, Serializable {

    private String systemId;
    private int lineNumber;
    private int columnNumber = -1;

    /**
     * Create an ExpressionLocation
     */

    public ExpressionLocation() {}

    /**
     * Create an ExpressionLocation, taking the data from a supplied JAXP SourceLocator
     * @param loc the JAXP SourceLocator
     */

    public ExpressionLocation(SourceLocator loc) {
        systemId = loc.getSystemId();
        lineNumber = loc.getLineNumber();
        columnNumber = loc.getColumnNumber();
    }

    /**
     * Create an ExpressionLocation, taking the data from a supplied SAX Locator
     * @param loc the SAX Locator
     */

    public static ExpressionLocation makeFromSax(Locator loc) {
        return new ExpressionLocation(loc.getSystemId(), loc.getLineNumber(), loc.getColumnNumber());
    }

    /**
     * Create an ExpressionLocation, taking the data from a supplied locationId along with a
     * LocationProvider to interpret its meaning
     * @param provider the LocationProvider
     * @param locationId the locationId
     */

    public ExpressionLocation(LocationProvider provider, long locationId) {
        systemId = provider.getSystemId(locationId);
        lineNumber = provider.getLineNumber(locationId);
        columnNumber = provider.getColumnNumber(locationId);
    }

    /**
     * Create an ExpressionLocation corresponding to a given module, line number, and column number
     * @param systemId the module URI
     * @param lineNumber the line number
     * @param columnNumber the column number
     */

    public ExpressionLocation(String systemId, int lineNumber, int columnNumber) {
        this.systemId = systemId;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    /**
     * Get the system ID (the module URI)
     * @return the system ID
     */

    public String getSystemId() {
        return systemId;
    }

    /**
     * Get the Public ID
     * @return always null in this implementation
     */

    /*@Nullable*/ public String getPublicId() {
        return null;
    }

    /**
     * Get the line number
     * @return the line number
     */

    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Get the column number
     * @return the column number
     */

    public int getColumnNumber() {
        return columnNumber;
    }

    /**
     * Set the systemId (the module URI)
     * @param systemId the systemId
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Set the line number
     * @param lineNumber the line number within the module
     */

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Set the column number
     * @param columnNumber  the column number
     */

    public void setColumnNumber(int columnNumber) {
        this.columnNumber = columnNumber;
    }

    /**
     * Get the system Id corresponding to a given location Id
     * @param locationId the location Id
     * @return the system Id
     */

    public String getSystemId(long locationId) {
        return getSystemId();
    }

    /**
     * Get the line number corresponding to a given location Id
     * @param locationId the location Id
     * @return the line number
     */

    public int getLineNumber(long locationId) {
        return getLineNumber();
    }

    public int getColumnNumber(long locationId) {
        return getColumnNumber();
    }

    /**
     * Construct an object holding location information for a validation error message
     * @param locationId The locationId as supplied with an event such as startElement or attribute
     * @param locationProvider The object that understands how to interpret the locationId
     * @return a SaxonLocator containing the location information
     */
    public static SaxonLocator getSourceLocator(long locationId, LocationProvider locationProvider) {
        SaxonLocator locator;
        if (locationProvider instanceof LocationMap && locationId != 0) {
            // this is typically true when validating output documents
            ExpressionLocation loc = new ExpressionLocation();
            loc.setLineNumber(locationProvider.getLineNumber(locationId));
            loc.setSystemId(locationProvider.getSystemId(locationId));
            locator = loc;
        } else if (locationProvider instanceof SaxonLocator) {
            // this is typically true when validating input documents
            locator = (SaxonLocator)locationProvider;
        } else {
            ExpressionLocation loc = new ExpressionLocation();
            loc.setLineNumber(locationProvider.getLineNumber(locationId));
            loc.setSystemId(locationProvider.getSystemId(locationId));
            locator = loc;
        }
        return locator;
    }

}