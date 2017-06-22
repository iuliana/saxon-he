////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

/**
 * The class acts as a register of Saxon-specific error codes.
 * <p>
 * Technically, these codes should be in their own namespace. At present, however, they share the
 * same namespace as system-defined error codes.
 */
public class SaxonErrorCode {

    /**
     * SXLM0001: stylesheet or query appears to be looping/recursing indefinitely
     */

    public static final String SXLM0001 = "SXLM0001";

    /**
     * SXCH0002: cannot supply output to ContentHandler because it is not well-formed
     */

    public static final String SXCH0002 = "SXCH0002";

    /**
     * SXCH0003: error reported by the ContentHandler (SAXResult) to which the result tree was sent
     */

    public static final String SXCH0003 = "SXCH0003";

    /**
     * SXCH0004: cannot load user-supplied ContentHandler
     */

    public static final String SXCH0004 = "SXCH0004";

    /**
     * SXRE0001: stack overflow within regular expression evaluation
     */

    public static final String SXRE0001 = "SXRE0001";

    /**
     * SXSE0001: cannot use character maps in an environment with no Controller
     */

    public static final String SXSE0001 = "SXSE0001";

    /**
     * SXSE0002: cannot use output property saxon:supply-source-locator unless tracing was enabled at compile time
     */

    public static final String SXSE0002 = "SXSE0002";

    /**
     * SXXP0003: error reported by XML parser while parsing source document
     */

    public static final String SXXP0003 = "SXXP0003";

    /**
     * SXXP0004: externally supplied node belongs to the wrong Configuration
     */

    public static final String SXXP0004 = "SXXP0004";

    /**
     * SXXP0005: namespace of source document doesn't match namespace of the template rules in the stylesheet
     */

    public static final String SXXP0005 = "SXXP0005";

    /**
     * SXXP0006: resource limits exceeded
     */

    public static final String SXXP0006 = "SXXP0006";

    /**
     * SXXF0001: first argument to saxon:eval must be an expression prepared using saxon:expression
     */

    public static final String SXXF0001 = "SXXF0001";

    /**
     * SXXF0002: undeclared namespace prefix used in saxon:script
     */

    public static final String SXXF0002 = "SXXF0002";

    /**
     * SXSQ0001: value of argument to SQL instruction is not a JDBC Connection object
     */

     public static final String SXSQ0001 = "SXSQ0001";

    /**
     * SXSQ0002: failed to close JDBC Connection
     */

     public static final String SXSQ0002 = "SXSQ0002";

    /**
     * SXSQ0003: failed to open JDBC Connection
     */

     public static final String SXSQ0003 = "SXSQ0003";

    /**
     * SXSQ0004: SQL Insert/Update/Delete action failed
     */

     public static final String SXSQ0004 = "SXSQ0004";

    /**
     * SXJE0001: cannot convert xs:boolean to the required Java type
     */

    public static final String SXJE0001 = "SXJE0001";

    /**
     * SXJE0002: cannot convert xs:double to the required Java type
     */

    public static final String SXJE0002 = "SXJE0002";

    /**
     * SXJE0003: cannot convert xs:duration to the required Java type
     */

    public static final String SXJE0003 = "SXJE0003";

    /**
     * SXJE0004: cannot convert xs:float to the required Java type
     */

    public static final String SXJE0004 = "SXJE0004";

    /**
     * SXJE0005: cannot convert xs:string to Java char unless the length is exactly one
     */

    public static final String SXJE0005 = "SXJE0005";

    /**
     * SXJE0006: cannot convert xs:string to the required Java type
     */

    public static final String SXJE0006 = "SXJE0006";

    /**
     * SXJE0007: cannot convert xs:dayTimeDuration to the required Java type
     */

    public static final String SXJE0007 = "SXJE0007";

    /**
     * SXJE0008: cannot convert xs:yearMonthDuration to the required Java type
     */

    public static final String SXJE0008 = "SXJE0008";

    /**
     * SXJE0009: cannot atomize an external Object
     */

    public static final String SXJE0009 = "SXJE0009";

    /**
     * SXJE0021: cannot convert XPath value to the type required by the signature of an extension function
     */

    public static final String SXJE0021 = "SXJE0021";

    /**
     * SXJE0022: cannot convert XPath value to the type required by the signature of an extension function,
     * the XPath value is a sequence of more than one item but the Java type is a singleton
     */

    public static final String SXJE0022 = "SXJE0022";

    /**
     * SXJE0023: cannot convert XPath item to the member type of a Java array
     */

    public static final String SXJE0023 = "SXJE0023";

    /**
     * SXJE0051: supplied Java List/Array contains a member that cannot be converted to an Item
     */

    public static final String SXJE0051 = "SXJE0051";

    /**
     * SXJE0052: exception thrown by extension function
     */

    public static final String SXJE0052 = "SXJE0052";

    /**
     * SXST0060: Template in a streaming mode is not streamable
     */

    public static final String SXST0060 = "SXST0060";

    /**
     * SXST0061: Requested initial mode is streamable; must supply SAXSource or StreamSource
     */

    public static final String SXST0061 = "SXST0061";

    /**
     * SXST0062: Dynamic error: disallowed navigation from a streaming mode. Used when the streaming
     * analysis was optimistic, and the optimism proved unfounded.
     */

    public static final String SXST0062 = "SXST0062";

    /**
     * SXST0063: A streamable template returns nodes from the streamed input document
     */
    
    public static final String SXST0063 = "SXST0063";

    /**
     * SXST0064: A pattern in a streamable template uses predicates that reposition the input stream
     */

    public static final String SXST0064 = "SXST0064";

    /**
     * SXST0065: Cannot use tracing with streaming templates
     */

    public static final String SXST0065 = "SXST0065";

    /**
     * SXST0066: Within a conditional expression in a streamed template, the condition cannot read the streamed input
     */

    public static final String SXST0066 = "SXST0066";

    /**
     * SXST0067: Within a streamed template, cannot call last()
     */

    public static final String SXST0067 = "SXST0067";

    /**
     * SXST0068:  Parameters of a streamed template, cannot consume the input stream
     */

    public static final String SXST0068 = "SXST0068";

    /**
     * SXST0069:  A parameter to a streamed template must not reference a node in the streamed document
     */

    public static final String SXST0069 = "SXST0069";

     /**
     * SXST0070:  Body of for-each must not select from streamed document unless the select expression does so
     */

    public static final String SXST0070 = "SXST0070";

    /**
    * SXST0071:  Cannot navigate upwards and then downwards when streaming
    */

   public static final String SXST0071 = "SXST0071";



    /**
     * SXUP0081: attempt to update a non-updateable node
     */

    public static final String SXUP0081 = "SXUP0081";


    /**
     * SXWN9001: a variable declaration with no following siblings has no effect
     */

    public static final String SXWN9001 = "SXWN9001";

    /**
     * SXWN9002: saxon:indent-spaces must be a positive integer
     */

    public static final String SXWN9002 = "SXWN9002";

    /**
     * SXWN9003: saxon:require-well-formed must be "yes" or "no"
     */

    public static final String SXWN9003 = "SXWN9003";

    /**
     * SXWN9004: saxon:next-in-chain cannot be specified dynamically
     */

    public static final String SXWN9004 = "SXWN9004";

    /**
     * SXWN9005: The 'default' attribute of saxon:collation no longer has any effect
     */

    public static final String SXWN9005 = "SXWN9005";

    /**
     * SXWN9006: No schema-location was specified, and no schema with the requested target namespace
     * is known, so the schema import was ignored
     */

    public static final String SXWN9006 = "SXWN9006";

    /**
     * SXWN9008: Saxon extension element not recognized because namespace not declared
     * in extension-element-prefixes
     */

    public static final String SXWN9008 = "SXWN9008";

    /**
     * SXWN9009: an empty xsl:for-each or xsl:for-each-group has no effect
     */

    public static final String SXWN9009 = "SXWN9009";

    /**
     * SXWN9010: saxon:recognize-binary must be "yes" or "no"
     */

    public static final String SXWN9010 = "SXWN9010";

    /**
     * SXWN9011: saxon:memo-function ignored under Saxon-HE
     */

    public static final String SXWN9011 = "SXWN9011";

    /**
     * SXWN9012: saxon:threads ignored when compiling with trace enabled
     */

    public static final String SXWN9012 = "SXWN9012";

    /**
     * SXWN9013: saxon:threads ignored when not running under Saxon-EE
     */

    /*@NotNull*/ public static final String SXWN9013 = "SXWN9013";
}

