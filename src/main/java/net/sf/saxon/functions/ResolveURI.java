////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
* This class supports the resolve-uri() functions in XPath 2.0
*/

public class ResolveURI extends SystemFunctionCall implements Callable {

    /*@Nullable*/ String expressionBaseURI = null;

    @Override
    public void bindStaticContext(StaticContext env) throws XPathException {
        expressionBaseURI = env.getBaseURI();
    }

    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
        if (expressionBaseURI == null) {
            expressionBaseURI = visitor.getStaticContext().getBaseURI();
            if (expressionBaseURI == null && argument.length == 1) {
                XPathException de = new XPathException("Base URI in static context of resolve-uri() is unknown");
                de.setErrorCode("FONS0005");
                throw de;
            }
        }
    }

    /**
     * Get the static base URI of the expression
     * @return the base URI from the static context
     */

    /*@Nullable*/ public String getStaticBaseURI() {
        return expressionBaseURI;
    }

    /**
     * Copy an expression. This makes a deep copy.
     * @return the copy of the original expression
     */

    /*@NotNull*/ public Expression copy() {
        ResolveURI d = (ResolveURI)super.copy();
        d.expressionBaseURI = expressionBaseURI;
        return d;
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(/*@NotNull*/ Object o) {
        return (o instanceof ResolveURI) &&
                super.equals(o) &&
                equalOrNull(expressionBaseURI, ((ResolveURI)o).expressionBaseURI);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (expressionBaseURI != null ? expressionBaseURI.hashCode() : 0);
        return result;
    }

    /**
    * Evaluate the function at run-time
    */

    /*@Nullable*/ public AnyURIValue evaluateItem(XPathContext context) throws XPathException {
        AtomicValue arg0 = (AtomicValue)argument[0].evaluateItem(context);
        if (arg0 == null) {
            return null;
        }
        String relative = arg0.getStringValue();
        String base;
        if (argument.length == 2) {
            base = argument[1].evaluateAsString(context).toString();
        } else {
            base = expressionBaseURI;
            if (expressionBaseURI == null) {
                dynamicError("Base URI in static context of resolve-uri() is unknown", "FONS0005", context);
                return null;
            }
        }

        return resolve(base, relative, context);
    }

    /**
     * Evaluate the expression
     *
     *
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    /*@Nullable*/ public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue arg0 = (AtomicValue)arguments[0].head();
        if (arg0 == null) {
            return EmptySequence.getInstance();
        }
        String relative = arg0.getStringValue();
        String base;
        if (argument.length == 2) {
            //noinspection ConstantConditions
            base = arguments[1].head().getStringValue();
        } else {
            base = expressionBaseURI;
            if (expressionBaseURI == null) {
                dynamicError("Base URI in static context of resolve-uri() is unknown", "FONS0005", context);
                return null;
            }
        }

        return resolve(base, relative, context);
    }

    /*@NotNull*/ private AnyURIValue resolve(String base, String relative, XPathContext context) throws XPathException {
        try {
            URI absoluteURI = new URI(base);
            if (!absoluteURI.isAbsolute()) {
                URI relativeURI = new URI(relative);
                if (relativeURI.isAbsolute()) {
                    return new AnyURIValue(relative);
                }
                dynamicError("Base URI " + Err.wrap(base) + " is not an absolute URI", "FORG0002", context);
            }
            if (absoluteURI.isOpaque()) {
                // Special-case JAR file URLs, even though non-conformant
                if (base.startsWith("jar:file:")) {
                    try {
                        URL absoluteURL = new URL(base);
                        URL resolved = new URL(absoluteURL, relative);
                        return new AnyURIValue(resolved.toString());
                    } catch (Exception err) {
                        // fall through to report error
                    }
                }
                dynamicError("Base URI " + Err.wrap(base) + " is a non-hierarchic URI", "FORG0002", context);
            }
            if (absoluteURI.getRawFragment() != null) {
                dynamicError("Base URI " + Err.wrap(base) + " contains a fragment identifier", "FORG0002", context);
            }
            URI resolved = makeAbsolute(relative,  base);
            return new AnyURIValue(resolved.toString());
        } catch (URISyntaxException err) {
            dynamicError("Base URI " + Err.wrap(base) + " is invalid: " + err.getMessage(),
                    "FORG0002", context);
            return new AnyURIValue(""); //dummy return to satisfy the language rules
        }
    }

    /**
     * If a system ID can't be parsed as a URL, try to expand it as a relative
     * URI using the current directory as the base URI.
     * @param systemId the supplied systemId. Null is treated as equivalent to ""
     * @return the systemId itself if it is a valid URL; otherwise the result of resolving
     * the systemId as a relative file name in the current working directory; or if the
     * current working directory is not available (e.g. in an applet) the supplied systemId
     * unchanged (except that null is treated as "").
     */

    /*@NotNull*/
    public static String tryToExpand(/*@Nullable*/ String systemId) {
        if (systemId==null) {
            systemId = "";
        }
	    try {
	        new URL(systemId);
	        return systemId;   // all is well
	    } catch (MalformedURLException err) {
	        String dir;
	        try {
	            dir = System.getProperty("user.dir");
	        } catch (Exception geterr) {
	            // this doesn't work when running an applet
	            return systemId;
	        }
	        if (!(dir.endsWith("/") || systemId.startsWith("/"))) {
	            dir = dir + '/';
	        }

            try {
                URI currentDirectoryURI = new File(dir).toURI();
                URI baseURI = currentDirectoryURI.resolve(systemId);
                return baseURI.toString();
            } catch (Exception e) {
                return systemId;
            }

        }
	}

    /**
     * Construct an absolute URI from a relative URI and a base URI. The method uses the resolve
     * method of the java.net.URI class, except where the base URI uses the (non-standard) "jar:" scheme,
     * in which case the method used is <code>new URL(baseURL, relativeURL)</code>.
     *
     * <p>Spaces in either URI are converted to %20</p>
     *
     * <p>If no base URI is available, and the relative URI is not an absolute URI, then the current
     * directory is used as a base URI.</p>
     *
     * @param relativeURI the relative URI. Null is permitted provided that the base URI is an absolute URI
     * @param base        the base URI. Null is permitted provided that relativeURI is an absolute URI
     * @return the absolutized URI
     * @throws java.net.URISyntaxException if either of the strings is not a valid URI or
     * if the resolution fails
     */

    /*@NotNull*/
    public static URI makeAbsolute(/*@Nullable*/ String relativeURI, /*@Nullable*/ String base) throws URISyntaxException {
        URI absoluteURI;
        // System.err.println("makeAbsolute " + relativeURI + " against base " + base);
        if (relativeURI == null) {
            if (base == null) {
                throw new URISyntaxException("", "Relative and Base URI must not both be null");
            }
            absoluteURI = new URI(ResolveURI.escapeSpaces(base));
            if (!absoluteURI.isAbsolute()) {
                throw new URISyntaxException(base, "Relative URI not supplied, so base URI must be absolute");
            } else {
                return absoluteURI;
            }
        }
        relativeURI = ResolveURI.escapeSpaces(relativeURI);
        base = (base == null ? null : ResolveURI.escapeSpaces(base));
        try {
            if (base==null || base.length() == 0) {
                absoluteURI = new URI(relativeURI);
                if (!absoluteURI.isAbsolute()) {
                    String expandedBase = ResolveURI.tryToExpand(base);
                    if (!expandedBase.equals(base)) { // prevent infinite recursion
                        return makeAbsolute(relativeURI, expandedBase);
                    }
                }
            } else if (base.startsWith("jar:") || base.startsWith("file:////")) {

                // jar: URIs can't be resolved by the java.net.URI class, because they don't actually
                // conform with the RFC standards for hierarchic URI schemes (quite apart from not being
                // a registered URI scheme). But they seem to be widely used.

                // URIs starting file://// are accepted by the java.net.URI class, they are used to
                // represent Windows UNC filenames. However, the java.net.URI algorithm for resolving
                // a relative URI against such a base URI fails to produce a usable UNC filename (it's not
                // clear whether Java is implementing RFC 3986 correctly here, it depends on interpretation).
                // So we use the java.net.URL algorithm for this case too, because it works.

                try {
                    URL baseURL = new URL(base);
                    URL absoluteURL = new URL(baseURL, relativeURI);
                    absoluteURI = absoluteURL.toURI();
                        // Note JDK1.5 dependency on URL.toURI()
                } catch (MalformedURLException err) {
                    throw new URISyntaxException(base + " " + relativeURI, err.getMessage());
                }
            } else {
                URI baseURI;
                try {
                    baseURI = new URI(base);
                } catch (URISyntaxException e) {
                    throw new URISyntaxException(base, "Invalid base URI: " + e.getMessage());
                }
                if (baseURI.getFragment() != null) {
                    int hash = base.indexOf('#');
                    if (hash >= 0) {
                        base = base.substring(0, hash);
                    }
                    try {
                        baseURI = new URI(base);
                    } catch (URISyntaxException e) {
                        throw new URISyntaxException(base, "Invalid base URI: " + e.getMessage());
                    }
                }
                try {
                    new URI(relativeURI);   // for validation only
                } catch (URISyntaxException e) {
                    throw new URISyntaxException(base, "Invalid relative URI: " + e.getMessage());
                }
                absoluteURI = (relativeURI.length() == 0 ? baseURI : baseURI.resolve(relativeURI));
            }
        } catch (IllegalArgumentException err0) {
            // can be thrown by resolve() when given a bad URI
            throw new URISyntaxException(relativeURI, "Cannot resolve URI against base " + Err.wrap(base));
        }

        return absoluteURI;
    }


    /**
     * Replace spaces by %20
     * @param s the input string
     * @return the input string with each space replaced by %20
     */

    /*@NotNull*/ public static String escapeSpaces(/*@NotNull*/ String s) {
        // It's not entirely clear why we have to escape spaces by hand, and not other special characters;
        // it's just that tests with a variety of filenames show that this approach seems to work.
        int i = s.indexOf(' ');
        if (i < 0) {
            return s;
        }
        return (i == 0 ? "" : s.substring(0, i))
                + "%20"
                + (i == s.length()-1 ? "" : escapeSpaces(s.substring(i+1)));
    }

}

