
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerConfigurationException;

/**
 * An exception that identifies an error in reading, parsing, or
 * validating a schema.
 */

public class SchemaException extends TransformerConfigurationException {

    /**
     * Creates a new XMLException with no message
     * or nested Exception.
     */

    public SchemaException() {
        super();
    }

    public SchemaException(String message, SourceLocator locator) {
        super(message, locator);
    }

    /**
     * Creates a new XMLException with the given message.
     *
     * @param message the message for this Exception
     */

    public SchemaException(String message) {
        super(message);
    }

    /**
     * Creates a new XMLException with the given nested
     * exception.
     *
     * @param exception the nested exception
     */

    public SchemaException(Throwable exception) {
        super(exception);
    }

    /**
     * Creates a new XMLException with the given message
     * and nested exception.
     *
     * @param message the detail message for this exception
     * @param exception the nested exception
     */

    public SchemaException(String message, Throwable exception) {
        super(message, exception);
    }

}

