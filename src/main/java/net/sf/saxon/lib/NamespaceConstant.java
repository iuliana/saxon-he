////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

/**
 * This class is not instantiated, it exists to hold a set of constants representing known
 * namespaces. For each of these, there is a constant for the namespace URI and for many of
 * them, there is a numeric constant used as the code for this namespace in the name pool.
 * <p/>
 * <p>This class also defines constant URIs for some objects other than namespaces -
 * for example, URIs that identify the various object models used in the JAXP XPath API,
 * and the Unicode codepoint collation URI.</p>
 *
 * @author Michael H. Kay
 */

public class NamespaceConstant {

    /**
     * A URI representing the null namespace (actually, an empty string)
     */

    public static final String NULL = "";
    /**
     * The numeric URI code representing the null namespace (actually, zero)
     */
    public static final short NULL_CODE = 0;

    /**
     * Fixed namespace name for XML: "http://www.w3.org/XML/1998/namespace".
     */
    public static final String XML = "http://www.w3.org/XML/1998/namespace";
    /**
     * Numeric code representing the XML namespace
     */
    public static final short XML_CODE = 1;


    /**
     * Fixed namespace name for XSLT: "http://www.w3.org/1999/XSL/Transform"
     */
    public static final String XSLT = "http://www.w3.org/1999/XSL/Transform";
    /**
     * Numeric code representing the XSLT namespace
     */
    public static final short XSLT_CODE = 2;

    /**
     * Fixed namespace name for SAXON: "http://saxon.sf.net/"
     */
    public static final String SAXON = "http://saxon.sf.net/";
    /**
     * Numeric code representing the SAXON namespace
     */
    public static final short SAXON_CODE = 3;

    /**
     * Namespace name for XML Schema: "http://www.w3.org/2001/XMLSchema"
     */
    public static final String SCHEMA = "http://www.w3.org/2001/XMLSchema";
    /**
     * Numeric code representing the schema namespace
     */
    public static final short SCHEMA_CODE = 4;

    /**
     * XML-schema-defined namespace for use in instance documents ("xsi")
     */
    public static final String SCHEMA_INSTANCE = "http://www.w3.org/2001/XMLSchema-instance";

    public static final short XSI_CODE = 5;

    /**
     * Namespace defined in XSD 1.1 for schema versioning
     */
    public static final String SCHEMA_VERSIONING = "http://www.w3.org/2007/XMLSchema-versioning";

    /**
     * Fixed namespace name for SAXON SQL extension: "http://saxon.sf.net/sql"
     */
    public static final String SQL = "http://saxon.sf.net/sql";

    /**
     * Fixed namespace name for EXSLT/Common: "http://exslt.org/common"
     */
    public static final String EXSLT_COMMON = "http://exslt.org/common";

    /**
     * Fixed namespace name for EXSLT/math: "http://exslt.org/math"
     */
    public static final String EXSLT_MATH = "http://exslt.org/math";

    /**
     * Fixed namespace name for EXSLT/sets: "http://exslt.org/sets"
     */
    public static final String EXSLT_SETS = "http://exslt.org/sets";

    /**
     * Fixed namespace name for EXSLT/date: "http://exslt.org/dates-and-times"
     */
    public static final String EXSLT_DATES_AND_TIMES = "http://exslt.org/dates-and-times";

    /**
     * Fixed namespace name for EXSLT/random: "http://exslt.org/random"
     */
    public static final String EXSLT_RANDOM = "http://exslt.org/random";

    /**
     * The standard namespace for functions and operators
     */
    public static final String FN = "http://www.w3.org/2005/xpath-functions";

    /**
     * The standard namespace for XQuery output declarations
     */
    public static final String OUTPUT = "http://www.w3.org/2010/xslt-xquery-serialization";


    /**
     * The standard namespace for system error codes
     */
    public static final String ERR = "http://www.w3.org/2005/xqt-errors";


    /**
     * Predefined XQuery namespace for local functions
     */
    public static final String LOCAL = "http://www.w3.org/2005/xquery-local-functions";

    /**
     * Math namespace for the XPath 3.0 math functions
     */

    public static final String MATH = "http://www.w3.org/2005/xpath-functions/math";

    /**
     * Namespace URI for XPath 3.0 functions associated with maps
     */
    public final static String MAP_FUNCTIONS = "http://www.w3.org/2005/xpath-functions/map";

    /**
     * Namespace URI for XPath 3.0 functions associated with maps as defined in the 2013 XSLT specification
     */
    public final static String MAP_FUNCTIONS_2011 = "http://www.w3.org/2011/xpath-functions/map";

    /**
     * Recognize the Microsoft namespace so we can give a suitably sarcastic error message
     */

    public static final String MICROSOFT_XSL = "http://www.w3.org/TR/WD-xsl";

    /**
     * The XHTML namespace http://www.w3.org/1999/xhtml
     */

    public static final String XHTML = "http://www.w3.org/1999/xhtml";

    /**
     * The SVG namespace
     */

    public static final String SVG = "http://www.w3.org/2000/svg";

    /**
     * The MathML namespace
     */

    public static final String MATHML = "http://www.w3.org/1998/Math/MathML";

    /**
     * The XMLNS namespace (used in DOM)
     */

    public static final String XMLNS = "http://www.w3.org/2000/xmlns/";

    /**
     * The XLink namespace
     */

    public static final String XLINK = "http://www.w3.org/1999/xlink";

    /**
     * The xquery-option namespace for the XQuery 3.0 feature names
     */

    public static final String XQUERY_OPTIONS = "http://www.w3.org/2011/xquery-options";

    /**
     * The xquery namespace for the XQuery 3.0 declare option
     */

    public static final String XQUERY = "http://www.w3.org/2012/xquery";

    /**
     * Namespace for types representing external Java objects
     */

    public static final String JAVA_TYPE = "http://saxon.sf.net/java-type";

    /**
     * Namespace for types representing external .NET objects
     */

    public static final String DOT_NET_TYPE = "http://saxon.sf.net/clitype";

    /**
     * Namespace for names allocated to anonymous types. This exists so that
     * a name fingerprint can be allocated for use as a type annotation.
     */

    public static final String ANONYMOUS = "http://ns.saxonica.com/anonymous-type";

    /**
     * Namespace for the Saxon serialization of the schema component model
     */

    public static final String SCM = "http://ns.saxonica.com/schema-component-model";

    /**
     * URI identifying the Saxon object model for use in the JAXP 1.3 XPath API
     */

    public static final String OBJECT_MODEL_SAXON = "http://saxon.sf.net/jaxp/xpath/om";


    /**
     * URI identifying the XOM object model for use in the JAXP 1.3 XPath API
     */

    public static final String OBJECT_MODEL_XOM = "http://www.xom.nu/jaxp/xpath/xom";

    /**
     * URI identifying the JDOM object model for use in the JAXP 1.3 XPath API
     */

    public static final String OBJECT_MODEL_JDOM = "http://jdom.org/jaxp/xpath/jdom";

    /**
     * URI identifying the AXIOM object model for use in the JAXP 1.3 XPath API
     */

    // TODO: get an official URI approved
    public static final String OBJECT_MODEL_AXIOM = "http://ws.apache.org/jaxp/xpath/axiom";

    /**
     * URI identifying the DOM4J object model for use in the JAXP 1.3 XPath API
     */

    public static final String OBJECT_MODEL_DOM4J = "http://www.dom4j.org/jaxp/xpath/dom4j";

    /**
     * URI identifying the .NET DOM object model (not used, but needed for consistency)
     */

    public static final String OBJECT_MODEL_DOT_NET_DOM = "http://saxon.sf.net/object-model/dotnet/dom";

    /**
     * URI identifying the Unicode codepoint collation
     */

    public static final String CODEPOINT_COLLATION_URI = "http://www.w3.org/2005/xpath-functions/collation/codepoint";

    /**
     * URI for the names of generated global variables
     */

    public static final String SAXON_GENERATED_GLOBAL = SAXON + "generated-global-variable";

    /**
     * URI for the Saxon configuration file
     */

    public static final String SAXON_CONFIGURATION = "http://saxon.sf.net/ns/configuration";

    /**
     * URI for the EXPath zip module
     */

    public static final String EXPATH_ZIP = "http://expath.org/ns/zip";


    /**
     * Private constructor: class is never instantiated
     */

    private NamespaceConstant() {
    }

    /**
     * Determine whether a namespace is a reserved namespace
     *
     * @param uri the namespace URI to be tested
     * @return true if this namespace URI is a reserved namespace
     */

    public static boolean isReserved(/*@Nullable*/ String uri) {
        return uri != null &&
                (uri.equals(XSLT) || uri.equals(FN) || uri.equals(XML) || uri.equals(SCHEMA) || uri.equals(SCHEMA_INSTANCE));
    }

    /**
     * Determine whether a namespace is a reserved namespace
     *
     * @param uri the namespace URI to be tested
     * @return true if this namespace URI is reserved in XQuery
     */

    public static boolean isReservedInQuery(String uri) {
        return uri.equals(FN) ||
                uri.equals(XML) ||
                uri.equals(SCHEMA) ||
                uri.equals(SCHEMA_INSTANCE);
    }

    /**
     * Determine whether a namespace is a reserved namespace
     *
     * @param uri the namespace URI to be tested
     * @return true if this namespace URI is reserved in XQuery
     */

    public static boolean isReservedInQuery30(String uri) {
        return uri.equals(FN) ||
                uri.equals(XML) ||
                uri.equals(SCHEMA) ||
                uri.equals(SCHEMA_INSTANCE) ||
                uri.equals(MATH) ||
                uri.equals(XQUERY) ||
                uri.equals(XQUERY_OPTIONS);
    }

    /**
     * Find a similar namespace to one that is a possible mis-spelling
     *
     * @param candidate the possibly mis-spelt namespace
     * @return the correct spelling of the namespace
     */

    public static String findSimilarNamespace(String candidate) {
        if (isSimilar(candidate, XML)) {
            return XML;
        } else if (isSimilar(candidate, SCHEMA)) {
            return SCHEMA;
        } else if (isSimilar(candidate, XSLT)) {
            return XSLT;
        } else if (isSimilar(candidate, SCHEMA_INSTANCE)) {
            return SCHEMA_INSTANCE;
        } else if (isSimilar(candidate, FN)) {
            return FN;
        } else if (isSimilar(candidate, SAXON)) {
            return SAXON;
        } else if (isSimilar(candidate, EXSLT_COMMON)) {
            return EXSLT_COMMON;
        } else if (isSimilar(candidate, EXSLT_MATH)) {
            return EXSLT_MATH;
        } else if (isSimilar(candidate, EXSLT_DATES_AND_TIMES)) {
            return EXSLT_DATES_AND_TIMES;
        } else if (isSimilar(candidate, EXSLT_RANDOM)) {
            return EXSLT_RANDOM;
        } else if (isSimilar(candidate, XHTML)) {
            return XHTML;
        } else if (isSimilar(candidate, ERR)) {
            return ERR;
        } else if (isSimilar(candidate, JAVA_TYPE)) {
            return JAVA_TYPE;
        } else if (isSimilar(candidate, DOT_NET_TYPE)) {
            return DOT_NET_TYPE;
        } else {
            return null;
        }
    }

    private static boolean isSimilar(String s1, String s2) {
        if (s1.equalsIgnoreCase(s2)) {
            return true;
        } else if (s1.startsWith(s2) && s1.length() - s2.length() < 3) {
            return true;
        } else if (s2.startsWith(s1) && s2.length() - s1.length() < 3) {
            return true;
        } else if (s1.length() > 8 && Math.abs(s2.length() - s1.length()) < 3) {
            int diff = 0;
            for (int i = 0; i < s1.length(); i++) {
                char c1 = s1.charAt(i);
                if (!((i < s2.length() && c1 == s2.charAt(i))
                        || (i > 0 && i < s2.length() - 1 && c1 == s2.charAt(i - 1))
                        || (i + 1 < s2.length() && c1 == s2.charAt(i + 1)))) {
                    diff++;
                }
            }
            return diff < 3;
        } else {
            return false;
        }
    }
}

