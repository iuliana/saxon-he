////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.expath.zip.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.option.expath.zip.library.ZipFacade;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;


/**
 * The implementation of the text-entry, a function of the EXPath zip module
 * Extracts the contents of the text file positioned at entry within the ZIP
 * file identified by $href and returns it as a string
 * */
public class TextEntry extends ExtensionFunctionDefinition {
    private final static StructuredQName name =
            new StructuredQName("", NamespaceConstant.EXPATH_ZIP, "text-entry");

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
        return SequenceType.OPTIONAL_STRING;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new ExtensionFunctionCall() {
            private String baseURI;
            @Override
            public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

                AnyURIValue href = (AnyURIValue)arguments[0].head();
                StringValue path = (StringValue)arguments[1].head();
                assert href != null;
                assert path != null;
                ZipFacade zip = new ZipFacade(baseURI);
                String content = zip.textEntry(href.getStringValue(), path.getStringValue());
                return (content == null ? EmptySequence.getInstance() : new StringValue(content));
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

