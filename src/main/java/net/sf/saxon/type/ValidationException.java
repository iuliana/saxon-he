////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.AbsolutePath;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import org.xml.sax.Locator;

import javax.xml.transform.SourceLocator;

/**
 * This exception indicates a failure when validating an instance against a type
 * defined in a schema.
 */

public class ValidationException extends XPathException
        implements SourceLocator, Locator {

    private String systemId;
    private String publicId;
    private int lineNumber = -1;
    private int columnNumber = -1;
    /*@Nullable*/ private NodeInfo node;
    private int schemaPart = -1;
    private String constraintName;
    private String constraintClauseNumber;
    private AbsolutePath path;
    private AbsolutePath contextPath;
    private SchemaType type;

    // TODO: during output validation, it would sometimes be useful to know what the position in the input file was.

    /**
     * Creates a new ValidationException with the given message.
     * @param message the message for this Exception
     */

    public ValidationException(String message) {
        super(message);
        setIsTypeError(true);
    }

    /**
     * Creates a new ValidationException with the given nested
     * exception.
     * @param exception the nested exception
     */
    public ValidationException(Exception exception) {
        super(exception);
        setIsTypeError(true);
    }

    /**
     * Creates a new ValidationException with the given message
     * and nested exception.
     * @param message the detail message for this exception
     * @param exception the nested exception
     */
    public ValidationException(String message, Exception exception) {
        super(message, exception);
        setIsTypeError(true);
    }

    /**
     * Create a new ValidationException from a message and a Locator.
     * @param message The error or warning message.
     * @param locator The locator object for the error or warning.
     */
    public ValidationException(String message, SourceLocator locator) {
        super(message, locator);
        setIsTypeError(true);
        // With Xerces, it's enough to store the locator as part of the exception. But with Crimson,
        // the locator is destroyed when the parse terminates, which means the location information
        // will not be available in the final error message. So we copy the location information now,
        // as part of the exception object itself.
        setSourceLocator(locator);
    }

    /**
     * Set a reference to the constraint in XML Schema that is not satisfied
     * @param schemaPart - 1 or 2, depending whether the constraint is in XMLSchema part 1 or part 2
     * @param constraintName - the short name of the constraint in XMLSchema, as a fragment identifier in the
     * HTML of the XML Schema Part 1 specification
     * @param clause - the clause number within the description of that constraint
     */

    public void setConstraintReference(int schemaPart, String constraintName, String clause) {
        this.schemaPart = schemaPart;
        this.constraintName = constraintName;
        this.constraintClauseNumber = clause;
    }

    /**
     * Copy the constraint reference from another exception object
     * @param e the other exception object from which to copy the information
     */

    public void setConstraintReference(/*@NotNull*/ ValidationException e) {
        schemaPart = e.schemaPart;
        constraintName = e.constraintName;
        constraintClauseNumber = e.constraintClauseNumber;
    }

    /**
     * Get the constraint reference as a string for inserting into an error message.
     * @return the reference as a message, or null if no information is available
     */

    /*@Nullable*/ public String getConstraintReferenceMessage() {
        if (schemaPart == -1) {
            return null;
        }
        return "See http://www.w3.org/TR/xmlschema11-" + schemaPart + "/#" + constraintName
                + " clause " + constraintClauseNumber;
    }

    /**
     * Get the "schema part" component of the constraint reference
     * @return 1 or 2 depending on whether the violated constraint is in XML Schema Part 1 or Part 2;
     * or -1 if there is no constraint reference
     */

    public int getConstraintSchemaPart() {
        return schemaPart;
    }

    /**
     * Get the constraint name
     * @return the name of the violated constraint, in the form of a fragment identifier within
     * the published XML Schema specification; or null if the information is not available.
     */

    /*@Nullable*/
    public String getConstraintName() {
        return constraintName;
    }

    /**
     * Get the constraint clause number
     * @return the section number of the clause containing the constraint that has been violated.
     * Generally a decimal number in the form n.n.n; possibly a sequence of such numbers separated
     * by semicolons. Or null if the information is not available.
     */

    /*@Nullable*/
    public String getConstraintClauseNumber() {
        return constraintClauseNumber;
    }

    /**
     * Get the constraint name and clause in the format defined in XML Schema Part C (Outcome Tabulations).
     * This mandates the format validation-rule-name.clause-number
     * @return the constraint reference, for example "cos-ct-extends.1.2"; or null if the reference
     * is not known.
     */

    /*@NotNull*/ public String getConstraintReference() {
        return (constraintName == null ? "" : constraintName) + '.' +
                (constraintClauseNumber == null ? "" : constraintClauseNumber);
    }

    /**
     * Set the path in the source document
     * @param path the path to the invalid element in the source document
     */

    public void setPath(AbsolutePath path) {
        this.path = path;
    }

     /**
     * Returns the String representation of this Exception
     * @return the String representation of this Exception
    **/
    public String toString() {
        StringBuilder sb = new StringBuilder("ValidationException: ");
        String message = getMessage();
        if (message != null) {
            sb.append(message);
        }
        return sb.toString();
    }

    public String getPublicId() {
        SourceLocator loc = getLocator();
        if (publicId == null && loc != null && loc != this) {
            return loc.getPublicId();
        } else{
            return publicId;
        }
    }

    public String getSystemId() {
        SourceLocator loc = getLocator();
        if (systemId == null && loc != null && loc != this) {
            return loc.getSystemId();
        } else{
            return systemId;
        }
    }

    public int getLineNumber() {
        SourceLocator loc = getLocator();
        if (lineNumber == -1 && loc != null && loc != this) {
            return loc.getLineNumber();
        } else{
            return lineNumber;
        }
    }

    public int getColumnNumber() {
        SourceLocator loc = getLocator();
        if (columnNumber == -1 && loc != null && loc != this) {
            return loc.getColumnNumber();
        } else{
            return columnNumber;
        }
    }

    /*@Nullable*/ public NodeInfo getNode() {
        return node;
    }

    /**
     * Get the location of the error in terms of a path expressed as a string
     * @return the location, as a path. The result format is similar to that of the fn:path() function
     */

    /*@Nullable*/ public String getPath() {
        if (path != null) {
            return path.getPathUsingAbbreviatedUris();
        } else if (node != null) {
            return Navigator.getPath(node);
        } else {
            return null;
        }
    }

    /**
     * Get the location of the error as a structured path object
     * @return the location, as a structured path object indicating the position of the error within the containing document
     */

    public AbsolutePath getAbsolutePath() {
        if (path != null) {
            return path;
        } else if (node != null) {
            return Navigator.getAbsolutePath(node);
        } else {
            return null;
        }
    }

    public void setPublicId(String id) {
        publicId = id;
    }

    public void setSystemId(String id) {
        systemId = id;
    }

    public void setLineNumber(int line) {
        lineNumber = line;
    }

    public void setColumnNumber(int column) {
        columnNumber = column;
    }

    public void setLocator(/*@Nullable*/ Locator locator) {
        if (locator != null) {
            setPublicId(locator.getPublicId());
            setSystemId(locator.getSystemId());
            setLineNumber(locator.getLineNumber());
            setColumnNumber(locator.getColumnNumber());
            if (locator instanceof NodeInfo) {
                node = ((NodeInfo)locator);
            }
        }
        super.setLocator(null);
    }

    public void setSourceLocator(/*@Nullable*/ SourceLocator locator) {
        if (locator != null) {
            setPublicId(locator.getPublicId());
            setSystemId(locator.getSystemId());
            setLineNumber(locator.getLineNumber());
            setColumnNumber(locator.getColumnNumber());
            if (locator instanceof NodeInfo) {
                node = ((NodeInfo)locator);
            }
        }
        super.setLocator(null);
    }

    public void setNode(NodeInfo node) {
        this.node = node;
    }

    public SourceLocator getLocator() {
        SourceLocator loc = super.getLocator();
        if (loc != null) {
            return loc;
        } else {
            return this;
        }
    }

    public void setContextPath(AbsolutePath path) {
        this.contextPath = path;
    }

    public AbsolutePath getContextPath() {
        return contextPath;
    }

    /**
     * Get additional location text, if any. This gives extra information about the position of the error
     * in textual form. Where XPath is embedded within a host language such as XSLT, the
     * formal location information identifies the location of the error in the XSLT module,
     * while this string locates the error within a specific XPath expression. The information
     * is typically used only for static errors.
     *
     * @return additional information about the location of the error, designed to be output
     *         as a prefix to the error message if desired. (It is not concatenated with the message, because
     *         it may be superfluous in an IDE environment.)
     */

    public String getValidationLocationText() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.MEDIUM);
        AbsolutePath valPath = getAbsolutePath();
        if (valPath != null) {
            fsb.append("Validating ");
            fsb.append(valPath.getPathUsingPrefixes());
            if (valPath.getSystemId() != null) {
                fsb.append(" in ");
                fsb.append(valPath.getSystemId());
            }
        }
        return fsb.toString();
    }


    /**
     * Get additional location text, if any. This gives extra information about the position of the error
     * in textual form. Where XPath is embedded within a host language such as XSLT, the
     * formal location information identifies the location of the error in the XSLT module,
     * while this string locates the error within a specific XPath expression. The information
     * is typically used only for static errors.
     *
     * @return additional information about the location of the error, designed to be output
     *         as a prefix to the error message if desired. (It is not concatenated with the message, because
     *         it may be superfluous in an IDE environment.)
     */

    public String getContextLocationText() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.MEDIUM);
        AbsolutePath contextPath = getContextPath();
        if (contextPath != null) {
            fsb.append("Currently processing ");
            fsb.append(contextPath.getPathUsingPrefixes());
            if (contextPath.getSystemId() != null) {
                fsb.append(" in ");
                fsb.append(contextPath.getSystemId());
            }
        }
        return fsb.toString();
    }

    /**
     * Get the schema type against which validation was attempted and failed
     * @return the relevant schema type if available, or null otherwise
     */

    public SchemaType getSchemaType() {
        return type;
    }

    /**
     * Set the schema type against which validation was attempted and failed
     * @param type the relevant schema type if available, or null otherwise
     */

    public void setSchemaType(SchemaType type) {
        this.type = type;
    }

}

