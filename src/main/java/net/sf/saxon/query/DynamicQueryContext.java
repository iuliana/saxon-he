////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.functions.Component;
import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.lib.UnparsedTextURIResolver;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.DateTimeValue;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.URIResolver;
import java.io.PrintStream;

/**
 * This object represents a dynamic context for query execution. This class is used
 * by the application writer to set up aspects of the dynamic context; it is not used
 * operationally (or modified) by the XQuery processor itself, which copies all required
 * information into its own internal representation.
 */

public class DynamicQueryContext {

    /*@Nullable*/ private Item contextItem;
    /*@Nullable*/ private GlobalParameterSet parameters;
    private Configuration config;
    private URIResolver uriResolver;
    private ErrorListener errorListener;
    /*@Nullable*/ private TraceListener traceListener;
    private UnparsedTextURIResolver unparsedTextURIResolver;
    private DateTimeValue currentDateTime;
    private PrintStream traceFunctionDestination;
    private int validationMode = Validation.DEFAULT;
    private boolean applyConversionRules = true;

    /**
     * Create the dynamic context for a query
     * @param config the Saxon configuration

     * @since 8.4.
     */

    public DynamicQueryContext(/*@NotNull*/ Configuration config) {
        this.config = config;
        uriResolver = config.getURIResolver();
        errorListener = config.getErrorListener();
        try {
            traceListener = config.makeTraceListener();
        } catch (XPathException err) {
            throw new IllegalStateException(err.getMessage());
        }
        if (errorListener instanceof StandardErrorListener) {
            errorListener = ((StandardErrorListener)errorListener).makeAnother(Configuration.XQUERY);
            ((StandardErrorListener)errorListener).setRecoveryPolicy(Configuration.DO_NOT_RECOVER);
        }
        traceFunctionDestination = config.getStandardErrorOutput();
    }

    /**
     * Ask whether source documents loaded using the doc(), document(), and collection()
     * functions, or supplied as a StreamSource or SAXSource to the transform() or addParameter() method
     * should be subjected to schema validation
     *
     * @return the schema validation mode previously set using setSchemaValidationMode(),
     *         or the default mode {@link net.sf.saxon.lib.Validation#DEFAULT} otherwise.
     */

    public int getSchemaValidationMode() {
        return validationMode;
    }

    /**
     * Say whether source documents loaded using the doc(), document(), and collection()
     * functions, or supplied as a StreamSource or SAXSource to the transform() or addParameter() method,
     * should be subjected to schema validation. The default value is taken
     * from the corresponding property of the Configuration.
     *
     * @param validationMode the validation (or construction) mode to be used for source documents.
     *                       One of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}, {@link net.sf.saxon.lib.Validation#STRICT},
     *                       {@link net.sf.saxon.lib.Validation#LAX}
     * @since 9.2
     */

    public void setSchemaValidationMode(int validationMode) {
        this.validationMode = validationMode;
    }

    /**
     * Say whether the function conversion rules should be applied to supplied
     * parameter values. For example, this allows an integer to be supplied as the value
     * for a parameter where the expected type is xs:double. The default is true.
     * @param convert true if function conversion rules are to be applied to supplied
     * values; if false, the supplied value must match the required type exactly.
     * @since 9.3
     */

    public void setApplyFunctionConversionRulesToExternalVariables(boolean convert) {
        applyConversionRules = convert;
    }

    /**
     * Ask whether the function conversion rules should be applied to supplied
     * parameter values. For example, this allows an integer to be supplied as the value
     * for a parameter where the expected type is xs:double. The default is true.
     * @return true if function conversion rules are to be applied to supplied
     * values; if false, the supplied value must match the required type exactly.
     * @since 9.3
     */

    public boolean isApplyFunctionConversionRulesToExternalVariables() {
        return applyConversionRules;
    }


    /**
     * Set the context item for evaluating the expression to be a node. If this method is not called,
     * the context node will be undefined. The context node is available as the value of
     * the expression ".".
     * To obtain a NodeInfo by parsing a source document, see the method
     * {@link net.sf.saxon.Configuration#buildDocument buildDocument}
     * in class QueryProcessor.
     *
     * @param node      The node that is to be the context node for the query
     * @since 8.4
     * @deprecated From Saxon 8.7, the method {@link #setContextItem(Item)} is preferred
     */

    public void setContextNode(NodeInfo node) {
        if (node==null) {
            throw new NullPointerException("Context node cannot be null");
        }
        setContextItem(node);
    }

    /**
     * Set the context item for evaluating the expression. If this method is not called,
     * the context node will be undefined. The context item is available as the value of
     * the expression ".",.
     * To obtain a node by parsing a source document, see the method
     * {@link net.sf.saxon.Configuration#buildDocument buildDocument}
     * in class QueryProcessor.
     * @param item The item that is to be the context item for the query
     * @throws IllegalArgumentException if the supplied item is a node that was built under the wrong
     * Saxon Configuration
     * @throws NullPointerException if the supplied item is null
     * @since 8.4
     */

    public void setContextItem(Item item) {
        if (item==null) {
            throw new NullPointerException("Context item cannot be null");
        }
        if (item instanceof NodeInfo) {
            if (!((NodeInfo)item).getConfiguration().isCompatible(config)) {
                throw new IllegalArgumentException(
                            "Supplied node must be built using the same or a compatible Configuration: " + SaxonErrorCode.SXXP0004);
            }
        }
        contextItem = item;
    }

     /**
     * Get the context item for the query, as set using setContextItem() or setContextNode().
     * @return the context item if set, or null otherwise.
     * @since 8.4
     */

    /*@Nullable*/ public Item getContextItem() {
        return contextItem;
    }

    /**
     * Set a parameter for the query.
     *
     * @param expandedName The name of the parameter in "{uri}local-name" format.
     *     It is not an error to supply a value for a parameter that has not been
     *     declared, the parameter will simply be ignored. If the parameter has
     *     been declared in the query (as an external global variable) then it
     *     will be initialized with the value supplied.
     * @param value The value of the parameter.  This can be any valid Java
     *     object.  It follows the same conversion rules as a value returned
     *     from a Saxon extension function. An error will occur at query
     *     execution time if the supplied value cannot be converted to the required
     *     type as declared in the query. For precise control of the type of the
     *     value, instantiate one of the classes in the net.sf.saxon.value package,
     *     for example net.sf.saxon.value.DayTimeDuration.
     * @since 8.4
     */

    public void setParameter(String expandedName, Object value) {
        if (parameters==null) {
            parameters = new GlobalParameterSet();
        }
        parameters.put(StructuredQName.fromClarkName(expandedName), value);
    }

    /**
     * Set a parameter for the query.
     *
     * @param expandedName The name of the parameter in "{uri}local-name" format.
     *     It is not an error to supply a value for a parameter that has not been
     *     declared, the parameter will simply be ignored. If the parameter has
     *     been declared in the query (as an external global variable) then it
     *     will be initialized with the value supplied.
     * @param value The value of the parameter.  This must be an XPath value in its Saxon
     *     representation: no conversion occurs. The value is not checked at this stage
     *     against its required type; such checking will happen later, when the query
     *     is executed.
     * @since 8.8
     */

    public void setParameterValue(String expandedName, Sequence value) {
        if (parameters==null) {
            parameters = new GlobalParameterSet();
        }
        parameters.put(StructuredQName.fromClarkName(expandedName), value);
    }


    /**
     * Reset the parameters to an empty list.
     */

    public void clearParameters() {
        parameters = null;
    }

    /**
     * Get the actual value of a parameter to the query.
     *
     * @param expandedName the name of the required parameter, in
     *     "{uri}local-name" format
     * @return the value of the parameter, if it exists, or null otherwise
     */

    /*@Nullable*/ public Object getParameter(String expandedName) {
        if (parameters==null) {
            return null;
        }
        return parameters.get(StructuredQName.fromClarkName(expandedName));
    }

    /**
     * Set all the supplied parameters as a HashMap. The key is the expanded QName in Clark notation,
     * the value is the value as supplied to setParameterValue
     * @param params a structure containing all the parameters
     */

    public void setParameters(GlobalParameterSet params) {
        parameters = params;
    }

    /**
     * Get all the supplied parameters.
     * @return a structure containing all the parameters
     */

    public GlobalParameterSet getParameters() {
        if (parameters == null) {
            return new GlobalParameterSet();
        } else {
            return parameters;
        }
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * fn:document() and related functions.
     *
     * @param resolver An object that implements the URIResolver interface, or
     *      null.
     * @since 8.4
     */

    public void setURIResolver(URIResolver resolver) {
        // System.err.println("Setting uriresolver to " + resolver + " on " + this);
        uriResolver = resolver;
    }

    /**
     * Get the URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or the
     *     system-defined one otherwise
     * @since 8.4
     */

    public URIResolver getURIResolver() {
        return uriResolver;
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * fn:unparsed-text() and related functions.
     *
     * @param resolver An object that implements the UnparsedTextURIResolver interface, or
     *      null.
     * @since 9.5
     */

    public void setUnparsedTextURIResolver(UnparsedTextURIResolver resolver) {
        // System.err.println("Setting uriresolver to " + resolver + " on " + this);
        unparsedTextURIResolver = resolver;
    }

    /**
     * Get the URI resolver for unparsed text.
     *
     * @return the user-supplied unparsed text URI resolver if there is one, or the
     *     system-defined one otherwise
     * @since 8.4
     */

    public UnparsedTextURIResolver getUnparsedTextURIResolver() {
        return unparsedTextURIResolver;
    }


	/**
	 * Set the error listener. The error listener receives reports of all run-time
     * errors and can decide how to report them.
	 *
	 * @param listener the ErrorListener to be used
     * @since 8.4
	 */

	public void setErrorListener(ErrorListener listener) {
		errorListener = listener;
	}

	/**
	 * Get the error listener.
	 *
	 * @return the ErrorListener in use
     * @since 8.4
	 */

	public ErrorListener getErrorListener() {
		return errorListener;
	}

    /**
     * Set the trace listener. The trace listener receives reports of all run-time
     * expression evaluation.
     *
     * @param listener the TraceListener to be used
     * @since 9.0
     */

    public void setTraceListener(/*@Nullable*/ TraceListener listener) {
        traceListener = listener;
    }

    /**
     * Get the trace listener.
     *
     * @return the TraceListener in use, or null if none is in use
     * @since 9.0
     */

    /*@Nullable*/ public TraceListener getTraceListener() {
        return traceListener;
    }

    /**
     * Set the destination for output from the fn:trace() function.
     * By default, the destination is System.err. If a TraceListener is in use,
     * this is ignored, and the trace() output is sent to the TraceListener.
     * @param stream the PrintStream to which trace output will be sent. If set to
     * null, trace output is suppressed entirely. It is the caller's responsibility
     * to close the stream after use.
     * @since 9.1
     */

    public void setTraceFunctionDestination(/*@Nullable*/ PrintStream stream) {
        traceFunctionDestination = stream;
    }

    /**
     * Get the destination for output from the fn:trace() function.
     * @return the PrintStream to which trace output will be sent. If no explicitly
     * destination has been set, returns System.err. If the destination has been set
     * to null to suppress trace output, returns null.
     * @since 9.1
     */

    public PrintStream getTraceFunctionDestination() {
        return traceFunctionDestination;
    }


    /**
     * Get the date and time set previously using {@link #setCurrentDateTime(net.sf.saxon.value.DateTimeValue)}
     * or null if none has been set.
     * @return the current date and time, if it has been set.
     * @since 8.5
     */

    public DateTimeValue getCurrentDateTime() {
        return currentDateTime;
    }

    /**
     * Set a value to be used as the current date and time for the query. By default, the "real" current date and
     * time are used. The main purpose of this method is that it allows repeatable results to be achieved when
     * testing queries.
     * <p>This method also has the effect of setting the implicit timezone.</p>
     * @param dateTime The value to be used as the current date and time. This must include a timezone. The timezone
     * from this value will also be used as the implicit timezone
     * @since 8.5
     * @throws net.sf.saxon.trans.XPathException if the dateTime does not include a timezone
     */

    public void setCurrentDateTime(/*@NotNull*/ DateTimeValue dateTime) throws XPathException {
        currentDateTime = dateTime;
        if (dateTime.getComponent(Component.TIMEZONE) == null) {
            throw new XPathException("Supplied date/time must include a timezone");
        }
    }

    /**
     * Get the Configuration associated with this dynamic query context
     * @return the Configuration
     * @since 8.8
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Apply the settings from this DynamicQueryContext to a Controller
     * @param controller the Controller whose settings are to be initialized
     */

    public void initializeController(/*@NotNull*/ Controller controller) {
        for (StructuredQName name : getParameters().getKeys()) {
            String clarkName = name.getClarkName();
            controller.setParameter(clarkName, parameters.get(name));
        }
        controller.setURIResolver(getURIResolver());
        controller.setErrorListener(getErrorListener());
        controller.addTraceListener(getTraceListener());
        if (unparsedTextURIResolver != null) {
            controller.setUnparsedTextURIResolver(unparsedTextURIResolver);
        }
        controller.setTraceFunctionDestination(getTraceFunctionDestination());
        controller.setSchemaValidationMode(getSchemaValidationMode());
        DateTimeValue currentDateTime = getCurrentDateTime();
        if (currentDateTime != null) {
            try {
                controller.setCurrentDateTime(currentDateTime);
            } catch (XPathException e) {
                throw new AssertionError(e);    // the value should already have been checked
            }
        }
        controller.getBindery().setApplyFunctionConversionRulesToExternalVariables(applyConversionRules);
    }

}

