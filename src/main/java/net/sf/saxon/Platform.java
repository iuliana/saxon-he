////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.Serializable;
import java.util.List;
import java.util.Properties;

/**
 * This interface provides access to methods whose implementation depends on the chosen platform
 * (typically Java or .NET)
 */
public interface Platform extends Serializable {

    /**
     * Perform platform-specific initialization of the configuration
     * @param config the Saxon Configuration
     */

    public void initialize(Configuration config);

    /**
     * Return true if this is the Java platform
     * @return true if this is the Java platform
     */

    public boolean isJava();

    /**
     * Return true if this is the .NET platform
     * @return true if this is the .NET platform
     */

    public boolean isDotNet();

    /**
     * Get the platform version
     * @return the version of the platform, for example "Java version 1.5.09"
     */

    public String getPlatformVersion();

    /**
     * Get a suffix letter to add to the Saxon version number to identify the platform
     * @return "J" for Java, "N" for .NET
     */

    public String getPlatformSuffix();

    /**
     * Get a parser by instantiating the SAXParserFactory
     *
     * @return the parser (XMLReader)
     */

    public XMLReader loadParser();

    /**
     * Convert a StreamSource to either a SAXSource or a PullSource, depending on the native
     * parser of the selected platform
     * @param pipe the pipeline Configuration
     * @param input the supplied StreamSource
     * @param validation required validation mode, for example Validation.STRICT
     * @param dtdValidation true if DTD-based input validation is required
     * @param stripspace option for whitespace-stripping  (ALL, NONE, or IGNORABLE)
     * @return the PullSource or SAXSource, initialized with a suitable parser, or the original
     * input Source, if now special handling is required or possible
     */

    public Source getParserSource(PipelineConfiguration pipe, StreamSource input,
                                  int validation, boolean dtdValidation, int stripspace);

    /**
     * Obtain a collation with a given set of properties. The set of properties is extensible
     * and variable across platforms. Common properties with example values include lang=en-GB,
     * strength=primary, case-order=upper-first, ignore-modifiers=yes, alphanumeric=yes.
     * Properties that are not supported are generally ignored; however some errors, such as
     * failing to load a requested class, are fatal.
     * @param config the configuration object
     * @param props the desired properties of the collation
     * @param uri the collation URI
     * @return a collation with these properties
     * @throws XPathException if a fatal error occurs
     */

    /*@Nullable*/ public StringCollator makeCollation(Configuration config, Properties props, String uri) throws XPathException;

    /**
     * Given a collation, determine whether it is capable of returning collation keys.
     * The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * equal under the equals() method.
     * @param collation the collation being examined, provided as a Comparator
     * @return true if this collation can supply collation keys
     */

    public boolean canReturnCollationKeys(StringCollator collation);

    /**
     * Given a collation, get a collation key. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * equal under the equals() method.
     * @param namedCollation the collation in use
     * @param value the string whose collation key is required
     * @return a representation of the collation key, such that two collation keys are
     * equal() if and only if the string values they represent are equal under the specified collation.
     * @throws ClassCastException if the collation is not one that is capable of supplying
     * collation keys (this should have been checked in advance)
     */

    public Object getCollationKey(SimpleCollation namedCollation, String value);

    /**
     * Compile a regular expression
     *
     * @param regex the regular expression as a string
     * @param flags the value of the flags attribute
     * @param hostLanguage one of "XSD", XP20" or "XP30"
     * @param warnings if non-null, any warnings from the regular expression compiler will be added to this list.
     * If null, the warnings are ignored.
     * @return the compiled regular expression
     * @throws XPathException if the regular expression or the flags are invalid
     */

    public RegularExpression compileRegularExpression(CharSequence regex, String flags, String hostLanguage, List<String> warnings)
        throws XPathException;

    /**
     * Get a SchemaType representing a wrapped external (Java or .NET) object
     * @param config the Saxon Configuration
     * @param uri the namespace URI of the schema type
     * @param localName the local name of the schema type
     * @return the SchemaType object representing this type
     */

    public SchemaType getExternalObjectType(Configuration config, String uri, String localName);

    /**
     * Return the name of the directory in which the software is installed (if available)
     * @return the name of the directory in which Saxon is installaed, if available, or null otherwise
     * @param edition The edition of the software that is loaded ("HE", "PE", or "EE")
     * @param config the Saxon configuration
     */

    public String getInstallationDirectory(String edition, Configuration config);

    /**
     * Register all the external object models that are provided as standard
     * with the relevant edition of Saxon for this Configuration
     *
     * @since 9.3
     */

    public void registerAllBuiltInObjectModels(Configuration config);

    /**
     * Set the default XML parser to be loaded by the SAXParserFactory on this platform.
     * Needed because the Apache catalog resolver uses the SAXParserFactory to instantiate
     * a parser, and if not customized this causes a failure on the .NET platform.
     *
     * @since 9.4
     */

    public void setDefaultSAXParserFactory();




}

