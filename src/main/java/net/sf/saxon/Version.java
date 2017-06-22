////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

/**
 * The Version class holds the SAXON version information.
 */

public final class Version {

    private static final int[] STRUCTURED_VERSION = {9,5,1,9};
    private static final String VERSION = "9.5.1.9";
    private static final String BUILD = "110722"; //mmddhh
    private static final String RELEASE_DATE = "2015-04-13";
    private static final String MAJOR_RELEASE_DATE = "2013-04-17";

    private Version() {
        // class is never instantiated
    }

    /**
     * Return the name of this product. Supports the XSLT 2.0 system property xsl:product-name
     * @return the string "SAXON"
     */


    public static String getProductName() {
        return "SAXON";
    }

    /**
     * Return the name of the product vendor.
     * @return the string "Saxonica"
     */

    public static String getProductVendor() {
        return "Saxonica";
    }

   /**
     * Get the version number of the schema-aware version of the product
     * @param config the Saxon configuration
     * @return the version number of this version of Saxon, as a string
     */

   public static String getProductVariantAndVersion(Configuration config) {
       String edition = config.getEditionCode();
       if (edition.equals("PE") || edition.equals("EE")) {
           if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
               edition += " (unlicensed)";
           }
       }
        return edition + " " + getProductVersion();
    }

    /**
     * Get the user-visible version number of this version of the product
     * @return the version number of this version of Saxon, as a string: for example "9.0.1"
     */

    public static String getProductVersion() {
        return VERSION;
    }

    /**
     * Get the four components of the structured version number. This is used in the .NET product
     * to locate an assembly in the dynamic assembly cache: the assumption is that the third
     * and fourth components represent implementation changes rather than interface changes
     * @return  the four components of the version number, as an array: for example {9, 0, 1, 1}
     */

    public static int[] getStructuredVersionNumber() {
        return STRUCTURED_VERSION;
    }

    /**
     * Get the issue date of this version of the product. This will be the release date of the
     * latest maintenance release
     * @return the release date, as an ISO 8601 string
     */

    public static String getReleaseDate() {
        return RELEASE_DATE;
    }

    /**
     * Get the issue date of the most recent major release of the product, that is, a release offering
     * new functionality rather than just bug fixes (typically, a release in which the first two digits
     * of the version number change, for example 9.2 to 9.3).
     * @return the release date, as an ISO 8601 string
     */

    public static String getMajorReleaseDate() {
        return MAJOR_RELEASE_DATE;
    }


    /**
     * Get the version of the XSLT specification that this product supports
     * @return the string 2.0
     */

    public static String getXSLVersionString() {
        // TODO: not satisfactory if the user requested a 3.0 processor
        return "2.0";
    }

    /**
     * Get a message used to identify this product when a transformation is run using the -t option
     * @return A string containing both the product name and the product
     *     version
     */

    public static String getProductTitle() {
        return getProductName() + ' ' + getProductVersion() + " from Saxonica";
    }

    /**
     * Return a web site address containing information about the product. Supports the XSLT system property xsl:vendor-url
     * @return the string "http://saxon.sf.net/"
     */

    public static String getWebSiteAddress() {
        return "http://www.saxonica.com/";
    }

    /**
     * Invoking net.sf.saxon.Version from the command line outputs the build number
     * @param args not used
     */
    public static void main(String[] args) {
        System.err.println(getProductTitle() + " (build " + BUILD + ')');
    }
}

