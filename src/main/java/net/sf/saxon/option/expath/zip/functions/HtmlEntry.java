////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.expath.zip.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.option.expath.zip.library.ZipFacade;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

import javax.xml.transform.Source;


/**
 * The implementation of the html-entry, a function of the EXPath zip module
 * Extracts the html file positioned at entry within the ZIP file identified by $href, and returns a document node
 */
public class HtmlEntry extends ExtensionFunctionDefinition {
    private final static StructuredQName name =
            new StructuredQName("", NamespaceConstant.EXPATH_ZIP, "html-entry");

    @Override
    public StructuredQName getFunctionQName() {
        return name;
    }

    /**
     * Ask whether the function has side-effects. If the function does have side-effects, the optimizer
     * will be less aggressive in moving or removing calls to the function. However, calls on functions
     * with side-effects can never be guaranteed.
     *
     * @return true if the function has side-effects (including creation of new nodes, if the
     *         identity of those nodes is significant). The default implementation returns false.
     */
    @Override
    public boolean hasSideEffects() {
        return false;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_ANY_URI, SequenceType.SINGLE_STRING};
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.ALLOWS_ZERO_OR_ONE);
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new ExtensionFunctionCall() {
            private String baseURI;

            @Override
            public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

                AnyURIValue href = (AnyURIValue) arguments[0].head();
                StringValue path = (StringValue) arguments[1].head();
                assert href != null;
                assert path != null;
                String pathStr = path.getStringValue();
                if (pathStr.length() == 0) {
                    throw new XPathException("Empty path name not allowed", "FILE0001");
                }
                ZipFacade zip = new ZipFacade(baseURI);
                Source src = zip.htmlEntry(href.getStringValue(), path.getStringValue());
                if (src == null) {
                    throw new XPathException("The path " + path.getStringValue() + " does not exist", "FILE0001");
                }
                return context.getConfiguration().buildDocument(src);
            }

            @Override
            public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
                if (baseURI == null) {
                    baseURI = context.getBaseURI();
                }
            }
        };
    }
}

