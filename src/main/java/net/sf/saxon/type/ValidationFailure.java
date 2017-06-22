////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import org.xml.sax.Locator;

import javax.xml.transform.SourceLocator;

import static net.sf.saxon.expr.CastExpression.CAST_EX_CODE;

/**
 * This exception indicates a failure when validating an instance against a type
 * defined in a schema.
 *
 * <p>This class holds the same information as a ValidationException, except that it is not an exception,
 * and does not carry system overheads such as a stack trace. It is used because operations such as "castable",
 * and validation of values in a union, cause validation failures on a success path and it is costly to throw,
 * or even to create, exception objects on a success path.</p>
 */

public class ValidationFailure
        implements SourceLocator, Locator, ConversionResult {

    private String message;
    private String systemId;
    private String publicId;
    private int lineNumber = -1;
    private int columnNumber = -1;
    private int schemaPart = -1;
    private String constraintName;
    private String clause;
    /*@Nullable*/ private StructuredQName errorCode;

    /**
     * Creates a new ValidationException with the given message.
     * @param message the message for this Exception
    **/
    public ValidationFailure(String message) {
        this.message = message;
        setErrorCode(CAST_EX_CODE);
    }

    /**
     * Creates a new ValidationFailure with the given nested
     * exception.
     * @param exception the nested exception
    **/
    public ValidationFailure(/*@NotNull*/ Exception exception) {
        message = exception.getMessage();
        if (exception instanceof XPathException) {
            errorCode = ((XPathException)exception).getErrorCodeQName();
        } else {
            setErrorCode(CAST_EX_CODE);
        }
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
        this.clause = clause;
    }

    /**
     * Copy the constraint reference from another exception object
     * @param e the other exception object from which to copy the information
     */

    public void setConstraintReference(/*@NotNull*/ ValidationFailure e) {
        schemaPart = e.schemaPart;
        constraintName = e.constraintName;
        clause = e.clause;
    }

    /**
     * Get the constraint reference as a string for inserting into an error message.
     * @return the reference as a message, or null if no information is available
     */

    /*@Nullable*/ public String getConstraintReferenceMessage() {
        if (schemaPart == -1) {
            return null;
        }
        return "See http://www.w3.org/TR/xmlschema-" + schemaPart + "/#" + constraintName
                + " clause " + clause;
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

    public String getConstraintName() {
        return constraintName;
    }

    /**
     * Get the constraint clause number
     * @return the section number of the clause containing the constraint that has been violated.
     * Generally a decimal number in the form n.n.n; possibly a sequence of such numbers separated
     * by semicolons. Or null if the information is not available.
     */

    public String getConstraintClauseNumber() {
        return clause;
    }

    /**
     * Get the constraint name and clause in the format defined in XML Schema Part C (Outcome Tabulations).
     * This mandates the format validation-rule-name.clause-number
     * @return the constraint reference, for example "cos-ct-extends.1.2"; or null if the reference
     * is not known.
     */

    /*@NotNull*/ public String getConstraintReference() {
        return constraintName + '.' + clause;
    }


    public String getMessage() {
        return message;
    }

     /**
     * Returns the String representation of this Exception
     * @return the String representation of this Exception
    **/
    public String toString() {
        FastStringBuffer sb = new FastStringBuffer("ValidationException: ");
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

    public void setLocator(/*@Nullable*/ SourceLocator locator) {
        if (locator != null) {
            setPublicId(locator.getPublicId());
            setSystemId(locator.getSystemId());
            setLineNumber(locator.getLineNumber());
            setColumnNumber(locator.getColumnNumber());
        }
    }

    public void setSourceLocator(/*@Nullable*/ SourceLocator locator) {
        if (locator != null) {
            setPublicId(locator.getPublicId());
            setSystemId(locator.getSystemId());
            setLineNumber(locator.getLineNumber());
            setColumnNumber(locator.getColumnNumber());
        }
    }

    /*@NotNull*/ public SourceLocator getLocator() {
        return this;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = new StructuredQName("err", NamespaceConstant.ERR, errorCode);
    }

    public void setErrorCodeQName(StructuredQName errorCode) {
        this.errorCode = errorCode;
    }

    /*@Nullable*/ public String getErrorCode() {
        return errorCode == null ? null : errorCode.getLocalPart();
    }

    /*@Nullable*/ public StructuredQName getErrorCodeQName() {
        return errorCode;
    }

    /*@NotNull*/ public ValidationException makeException() {
        ValidationException ve = new ValidationException(message, getLocator());
        ve.setConstraintReference(schemaPart, constraintName, clause);
        if (errorCode == null) {
            ve.setErrorCode(CAST_EX_CODE);
        } else {
            ve.setErrorCodeQName(errorCode);
        }
        return ve;
    }

    /*@Nullable*/ public ValidationException makeException(/*@Nullable*/ String contextMessage) {
        ValidationException ve = new ValidationException((contextMessage == null ? message : contextMessage + message));
        ve.setConstraintReference(schemaPart, constraintName, clause);
        if (errorCode == null) {
            ve.setErrorCode(CAST_EX_CODE);
        } else {
            ve.setErrorCodeQName(errorCode);
        }
        return ve;
    }


    /**
     * Calling this method on a ConversionResult returns the AtomicValue that results
     * from the conversion if the conversion was successful, and throws a ValidationException
     * explaining the conversion error otherwise.
     * <p/>
     * <p>Use this method if you are calling a conversion method that returns a ConversionResult,
     * and if you want to throw an exception if the conversion fails.</p>
     *
     * @return the atomic value that results from the conversion if the conversion was successful
     * @throws net.sf.saxon.type.ValidationException
     *          if the conversion was not successful
     */

    /*@NotNull*/ public AtomicValue asAtomic() throws ValidationException {
        throw makeException();
    }
}

