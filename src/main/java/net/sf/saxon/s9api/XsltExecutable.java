////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.instruct.GlobalParam;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.value.SequenceType;

import java.util.HashMap;
import java.util.Map;

/**
 * An XsltExecutable represents the compiled form of a stylesheet.
 * To execute the stylesheet, it must first be loaded to form an {@link XsltTransformer}.
 *
 * <p>An XsltExecutable is immutable, and therefore thread-safe.
 *  It is simplest to load a new XsltTransformer each time the stylesheet is to be run.
 *  However, the XsltTransformer is serially reusable within a single thread. </p>
 *
 * <p>An XsltExecutable is created by using one of the <code>compile</code> methods on the
 * {@link XsltCompiler} class.</p>
 */
public class XsltExecutable {

    Processor processor;
    PreparedStylesheet pss;

    protected XsltExecutable(Processor processor, PreparedStylesheet pss) {
        this.processor = processor;
        this.pss = pss;
    }

    /**
     * Load the stylesheet to prepare it for execution.
     * @return  An XsltTransformer. The returned XsltTransformer can be used to set up the
     * dynamic context for stylesheet evaluation, and to run the stylesheet.
     */

    public XsltTransformer load() {
        return new XsltTransformer(processor, (Controller)pss.newTransformer());
    }

    /**
     * Produce a diagnostic representation of the compiled stylesheet, in XML form.
     * <p><i>The detailed form of this representation is not stable (or even documented).<i></p>
     * @param destination the destination for the XML document containing the diagnostic representation
     * of the compiled stylesheet
     * @since 9.1
     */

    public void explain(Destination destination) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        pss.explain(new ExpressionPresenter(config, destination.getReceiver(config)));
    }

    /**
     * Get the whitespace stripping policy defined by this stylesheet, that is, the policy
     * defined by the xsl:strip-space and xsl:preserve-space elements in the stylesheet
     * @return a newly constructed WhitespaceStrippingPolicy based on the declarations in this
     * stylesheet. This policy can be used as input to a {@link DocumentBuilder}.
     */

    public WhitespaceStrippingPolicy getWhitespaceStrippingPolicy() {
        return new WhitespaceStrippingPolicy(pss);
    }

    /**
     * Get the names of the xsl:param elements defined in this stylesheet, with details
     * of each parameter including its required type, and whether it is required or optional
     * @return a HashMap whose keys are the names of global parameters in the stylesheet,
     * and whose values are {@link ParameterDetails} objects giving information about the
     * corresponding parameter.
     * @since 9.3
     */

   /*@NotNull*/ public HashMap<QName, ParameterDetails> getGlobalParameters() {
        HashMap<StructuredQName, GlobalVariable> globals = pss.getCompiledGlobalVariables();
        HashMap<QName, ParameterDetails> params = new HashMap<QName, ParameterDetails>(globals.size());
        for (Map.Entry<StructuredQName, GlobalVariable> e : globals.entrySet()) {
            StructuredQName name = e.getKey();
            GlobalVariable var = e.getValue();
            if (var instanceof GlobalParam) {
                ParameterDetails details = new ParameterDetails(var.getRequiredType(), var.isRequiredParam());
                params.put(new QName(name), details);
            }
        }
        return params;
    }

    /**
     * Inner class containing information about a global parameter to a compiled stylesheet
     * @since 9.3
     */

    public class ParameterDetails {

        private SequenceType type;
        private boolean isRequired;

        protected ParameterDetails(SequenceType type, boolean isRequired) {
            this.type = type;
            this.isRequired = isRequired;
        }

        /**
         * Get the declared item type of the parameter
         * @return the type defined in the <code>as</code> attribute of the <code>xsl:param</code> element,
         * without its occurrence indicator
         */

        public ItemType getDeclaredItemType() {
            return new ConstructedItemType(type.getPrimaryType(), processor);
        }

        /**
         * Get the declared cardinality of the parameter
         * @return the occurrence indicator from the type appearing in the <code>as</code> attribute
         * of the <code>xsl:param</code> element
         */

        public OccurrenceIndicator getDeclaredCardinality() {
            return OccurrenceIndicator.getOccurrenceIndicator(type.getCardinality());
        }

        /**
         * Ask whether the parameter is required (mandatory) or optional
         * @return true if the parameter is mandatory (<code>required="yes"</code>), false
         * if it is optional
         */

        public boolean isRequired() {
            return this.isRequired;
        }
    }


    /**
     * Get the underlying implementation object representing the compiled stylesheet. This provides
     * an escape hatch into lower-level APIs. The object returned by this method may change from release
     * to release.
     * @return the underlying implementation of the compiled stylesheet
     */

    public PreparedStylesheet getUnderlyingCompiledStylesheet() {
        return pss;
    }
}

