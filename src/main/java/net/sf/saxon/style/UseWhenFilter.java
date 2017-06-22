////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.LocationProvider;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StartTagBuffer;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * This is a filter inserted into the input pipeline for processing stylesheet modules, whose
 * task is to evaluate use-when expressions and discard those parts of the stylesheet module
 * for which the use-when attribute evaluates to false.
 */

public class UseWhenFilter extends ProxyReceiver {

    private StartTagBuffer startTag;
    private int depthOfHole = 0;
    private boolean emptyStylesheetElement = false;
    private Stack defaultNamespaceStack = new Stack();
    private DateTimeValue currentDateTime = DateTimeValue.getCurrentDateTime(null);
    private PreparedStylesheet preparedStylesheet;
    private Map<StructuredQName, GroundedValue> staticVariables;


    /**
     * Create a UseWhenFilter
     *
     * @param preparedStylesheet the containing stylesheet
     * @param next               the next receiver in the pipeline
     */

    public UseWhenFilter(PreparedStylesheet preparedStylesheet, Receiver next) {
        super(next);
        this.preparedStylesheet = preparedStylesheet;
        staticVariables = new HashMap<StructuredQName, GroundedValue>();
    }

    /**
     * Set the start tag buffer
     *
     * @param startTag a preceding filter on the pipeline that buffers the attributes of a start tag
     */

    public void setStartTagBuffer(StartTagBuffer startTag) {
        this.startTag = startTag;
    }

    /**
     * Start of document
     */

    public void open() throws XPathException {
        nextReceiver.open();
    }

    /**
     * Notify the start of an element.
     *
     * @param elemName   the name of the element.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param properties bit-significant properties of the element node
     */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {
        boolean inXsltNamespace = elemName.isInNamespace(NamespaceConstant.XSLT);
        String stdAttUri = (inXsltNamespace ? "" : NamespaceConstant.XSLT);
        defaultNamespaceStack.push(startTag.getAttribute(stdAttUri, "xpath-default-namespace"));
        if (emptyStylesheetElement) {
            depthOfHole++;
            return;
        }
        if (depthOfHole == 0) {
            LocationProvider lp = getPipelineConfiguration().getLocationProvider();

            String useWhen = startTag.getAttribute(stdAttUri, "use-when");

            boolean isStylesheetElement = inXsltNamespace &&
                    (elemName.getLocalPart().equals("stylesheet") || elemName.getLocalPart().equals("transform"));

            if (isStylesheetElement) {
                String version = startTag.getAttribute("", "version");
                if ("3.0".equals(version)) {
                    preparedStylesheet.setHostLanguage(Configuration.XSLT, true);
                }
            }

            if (useWhen != null) {
                try {
                    boolean use = evaluateUseWhen(useWhen, lp.getLineNumber(locationId));
                    if (!use) {
                        if (isStylesheetElement) {
                            emptyStylesheetElement = true;
                        } else {
                            depthOfHole = 1;
                            return;
                        }
                    }
                } catch (XPathException e) {
                    XPathException err = createXPathException(
                            "Error in use-when expression. " + e.getMessage(), e.getErrorCodeLocalPart(), lp, locationId);
                    err.setErrorCodeQName(e.getErrorCodeQName());
                    throw err;
                }
            }

            if (inXsltNamespace && preparedStylesheet.isAllowXPath30()) {
                boolean isVariable = elemName.getLocalPart().equals("variable");
                boolean isParam = elemName.getLocalPart().equals("param");

                // Note, the general policy here is to ignore errors on this initial pass through the stylesheet,
                // if they will be checked and reported more thoroughly later on.

                if ((isVariable || isParam) &&
                        defaultNamespaceStack.size() == 2 &&
                        "yes".equals(Whitespace.trim(startTag.getAttribute("", "static")))) {

                    String nameStr = startTag.getAttribute("", "name");
                    String asStr = startTag.getAttribute("", "as");
                    boolean isRequired = "yes".equals(Whitespace.trim(startTag.getAttribute("", "required")));

                    UseWhenStaticContext staticContext = new UseWhenStaticContext(getConfiguration(), startTag, staticVariables);
                    SequenceType requiredType = SequenceType.ANY_SEQUENCE;
                    if (asStr != null) {
                        ExpressionParser parser = new ExpressionParser();
                        requiredType = parser.parseSequenceType(asStr, staticContext);
                    }

                    StructuredQName varName;
                    try {
                        varName = StructuredQName.fromLexicalQName(nameStr, false, true, getConfiguration().getNameChecker(), startTag);
                    } catch (XPathException err) {
                        throw createXPathException(
                                "Invalid variable name:" + nameStr + ". " + err.getMessage(),
                                err.getErrorCodeLocalPart(), lp, locationId);
                    }

                    boolean isSupplied = isParam && preparedStylesheet.getCompilerInfo().getParameters().containsKey(varName);
                    if (isParam) {
                        if (isRequired && !isSupplied) {
                            throw createXPathException(
                                    "No value was supplied for the required static parameter " + varName.getDisplayName(),
                                    "XTDE0050", lp, locationId);
                        }

                        if (isSupplied) {
                            Sequence suppliedValue = preparedStylesheet.getCompilerInfo().getParameters()
                                    .convertParameterValue(varName, requiredType, true, staticContext.makeEarlyEvaluationContext());

                            staticVariables.put(varName, SequenceTool.toGroundedValue(suppliedValue));
                        }
                    }

                    if (isVariable || !isSupplied) {
                        String selectStr = startTag.getAttribute("", "select");
                        Sequence value;
                        if (selectStr == null) {
                            if (isVariable) {
                                throw createXPathException(
                                        "The select attribute is required for a static global variable",
                                        "XTSE0010", lp, locationId);
                            } else {
                                value = EmptySequence.getInstance();
                                staticVariables.put(varName, EmptySequence.getInstance());
                            }

                        } else {
                            try {
                                value = evaluateStatic(selectStr, lp.getLineNumber(locationId));
                            } catch (XPathException e) {
                                throw createXPathException("Error in " + elemName.getLocalPart() + " expression. " + e.getMessage(),
                                        e.getErrorCodeLocalPart(), lp, locationId);
                            }
                        }
                        SourceLocator locator = new ExpressionLocation(lp, locationId);
                        RoleLocator role = new RoleLocator(RoleLocator.VARIABLE, varName, 0);
                        TypeHierarchy th = getConfiguration().getTypeHierarchy();
                        value = th.applyFunctionConversionRules(value, requiredType, role, locator);
                        staticVariables.put(varName, SequenceTool.toGroundedValue(value));
                    }
                }
            }

            nextReceiver.startElement(elemName, typeCode, locationId, properties);
        } else {
            depthOfHole++;
        }
    }

    public XPathException createXPathException(String message, String errorCode, LocationProvider lp, int locationId) throws XPathException {

        XPathException err = new XPathException(message);
        err.setErrorCode(errorCode);
        err.setIsStaticError(true);
        ExpressionLocation loc = new ExpressionLocation();
        loc.setSystemId(lp.getSystemId(locationId));
        loc.setLineNumber(lp.getLineNumber(locationId));
        err.setLocator(loc);

        try {
            getPipelineConfiguration().getErrorListener().fatalError(err);
        } catch (TransformerException tex) {
            throw XPathException.makeXPathException(tex);
        }
        err.setHasBeenReported(true);
        return err;
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBinding the namespace to be notified
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.namespace(namespaceBinding, properties);
        }
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param attName    The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, int locationId, int properties) throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.attribute(attName, typeCode, value, locationId, properties);
        }
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */

    public void startContent() throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.startContent();
        }
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        defaultNamespaceStack.pop();
        if (depthOfHole > 0) {
            depthOfHole--;
        } else {
            nextReceiver.endElement();
        }
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.characters(chars, locationId, properties);
        }
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, int locationId, int properties) {
        // these are ignored in a stylesheet
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, int locationId, int properties) throws XPathException {
        // these are ignored in a stylesheet
    }

    /**
     * Evaluate a use-when attribute
     *
     * @param expression the expression to be evaluated
     * @param locationId identifies the location of the expression in case error need to be reported
     * @return the effective boolean value of the result of evaluating the expression
     */

    public boolean evaluateUseWhen(String expression, int locationId) throws XPathException {
        UseWhenStaticContext staticContext = new UseWhenStaticContext(getConfiguration(), startTag, staticVariables);
        LocationProvider lp = getPipelineConfiguration().getLocationProvider();
        // TODO: The following doesn't take account of xml:base attributes
        staticContext.setBaseURI(lp.getSystemId(locationId));
        staticContext.setDefaultElementNamespace(NamespaceConstant.NULL);
        for (int i = defaultNamespaceStack.size() - 1; i >= 0; i--) {
            String uri = (String) defaultNamespaceStack.get(i);
            if (uri != null) {
                staticContext.setDefaultElementNamespace(uri);
                break;
            }
        }
        Expression expr = ExpressionTool.make(expression, staticContext,
                staticContext, 0, Token.EOF, lp.getLineNumber(locationId), null);
        expr.setContainer(staticContext);
        ItemType contextItemType = Type.ITEM_TYPE;
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(contextItemType, true);
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext, staticContext.getExecutable());
        expr = visitor.typeCheck(expr, cit);
        SlotManager stackFrameMap = getPipelineConfiguration().getConfiguration().makeSlotManager();
        ExpressionTool.allocateSlots(expr, stackFrameMap.getNumberOfVariables(), stackFrameMap);
        Controller controller = new Controller(getConfiguration());
        controller.setURIResolver(new URIPreventer());
        controller.setCurrentDateTime(currentDateTime);
        // this is to ensure that all use-when expressions in a module use the same date and time
        XPathContext dynamicContext = controller.newXPathContext();
        dynamicContext = dynamicContext.newCleanContext();
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return expr.effectiveBooleanValue(dynamicContext);
    }


    /**
     * Evaluate a use-when attribute
     *
     * @param expression the expression to be evaluated
     * @param locationId identifies the location of the expression in case error need to be reported
     * @return the effective boolean value of the result of evaluating the expression
     */

    public Sequence evaluateStatic(String expression, int locationId) throws XPathException {
        UseWhenStaticContext staticContext = new UseWhenStaticContext(getConfiguration(), startTag, staticVariables);
        LocationProvider lp = getPipelineConfiguration().getLocationProvider();
        // TODO: The following doesn't take account of xml:base attributes
        staticContext.setBaseURI(lp.getSystemId(locationId));
        staticContext.setDefaultElementNamespace(NamespaceConstant.NULL);
        for (int i = defaultNamespaceStack.size() - 1; i >= 0; i--) {
            String uri = (String) defaultNamespaceStack.get(i);
            if (uri != null) {
                staticContext.setDefaultElementNamespace(uri);
                break;
            }
        }
        Expression expr = ExpressionTool.make(expression, staticContext,
                staticContext, 0, Token.EOF, lp.getLineNumber(locationId), null);
        expr.setContainer(staticContext);
        ItemType contextItemType = Type.ITEM_TYPE;
        ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(contextItemType, true);
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext, staticContext.getExecutable());
        expr = visitor.typeCheck(expr, cit);
        SlotManager stackFrameMap = getPipelineConfiguration().getConfiguration().makeSlotManager();
        ExpressionTool.allocateSlots(expr, stackFrameMap.getNumberOfVariables(), stackFrameMap);
        Controller controller = new Controller(getConfiguration());
        controller.setURIResolver(new URIPreventer());
        controller.setCurrentDateTime(currentDateTime);
        // this is to ensure that all use-when expressions in a module use the same date and time
        XPathContext dynamicContext = controller.newXPathContext();
        dynamicContext = dynamicContext.newCleanContext();
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return SequenceExtent.makeSequenceExtent(expr.iterate(dynamicContext));
    }

    /**
     * Define a URIResolver that disallows all URIs
     */

    private static class URIPreventer implements URIResolver {
        /**
         * Called by the processor when it encounters
         * an xsl:include, xsl:import, or document() function.
         *
         * @param href An href attribute, which may be relative or absolute.
         * @param base The base URI against which the first argument will be made
         *             absolute if the absolute URI is required.
         * @return A Source object, or null if the href cannot be resolved,
         *         and the processor should try to resolve the URI itself.
         * @throws javax.xml.transform.TransformerException
         *          if an error occurs when trying to
         *          resolve the URI.
         */
        /*@NotNull*/
        public Source resolve(String href, String base) throws TransformerException {
            throw new TransformerException("No external documents are available within an [xsl]use-when expression");
        }
    }


}

