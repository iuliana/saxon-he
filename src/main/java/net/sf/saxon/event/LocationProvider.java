////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;


/**
 * LocationProvider: this interface represents an object that
 * provides the location of elements in a source document or instructions in a stylesheet
 * or query. A locationProvider may be passed down the Receiver pipeline as part of the
 * PipelineConfiguration object; on the input pipeline, this will be a {@link SaxonLocator} object,
 * on the output pipeline, it will be a {@link net.sf.saxon.expr.instruct.LocationMap}
 * <p/>
 * A LocationProvider that represents locations in the source document from which the events
 * are derived (as distinct from locations in a query or stylesheet of the instructions causing the
 * events) will also implement the marker interface {@link net.sf.saxon.event.SourceLocationProvider}
 */

public interface LocationProvider {

    /**
     * Get the URI of the document, entity, or module containing a particular location
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the URI of the document, XML entity or module. For a SourceLocationProvider this will
     * be the URI of the document or entity (the URI that would be the base URI if there were no
     * xml:base attributes). In other cases it may identify the query or stylesheet module currently
     * being executed.
     */

    public String getSystemId(long locationId);

    /**
     * Get the line number within the document, entity or module containing a particular location
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the line number within the document, entity or module, or -1 if no information is available.
     */

    public int getLineNumber(long locationId);

    /**
     * Get the column number within the document, entity, or module containing a particular location
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the column number within the document, entity, or module, or -1 if this is not available
     */

    public int getColumnNumber(long locationId);


}
