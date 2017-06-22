////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.parser.ExpressionLocation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This is a PullEvent representing the start of an element node. It contains (or potentially contains) all the
 * namespace declarations and attributes associated with the element.
 */
public class StartElementEvent implements PullEvent {

    PipelineConfiguration pipe;
    private NodeName elementName;
    private SchemaType typeCode;
    private NamespaceBinding[] localNamespaces;
    private List<NodeInfo> attributes;
    private int locationId = -1;

    /**
     * Create a Start Element Event
     * @param pipe the pipeline configuration
     */
    public StartElementEvent(PipelineConfiguration pipe) {
        this.pipe = pipe;
    }

    /**
     * Set the nameCode of this element
     * @param elementName the namecode of the element (its name as identified in the NamePool)
     */
    public void setElementName(NodeName elementName) {
        this.elementName = elementName;
    }

    /**
     * Get the nameCode of this element
     * @return the nameCode representing the element's name
     */
    public NodeName getElementName() {
        return elementName;
    }

    /**
     * Set the typeCode of this element
     * @param typeCode the element's type annotation
     */
    public void setTypeCode(SchemaType typeCode) {
        this.typeCode = typeCode;
    }

    /**
     * Get the typeCode of this element
     * @return the element's type annotation
     */
    public SchemaType getTypeCode() {
        return typeCode;
    }

    /**
     * Set the namespaces that are locally declared (or undeclared) on this element
     * @param nscodes integer array of namespace codes
     */
    public void setLocalNamespaces(NamespaceBinding[] nscodes) {
        localNamespaces = nscodes;
    }

    /** Add a namespace code representing a locally declared namespace
	 * 
	 * @param nscode a namespace code
	 * @throws XPathException */
	public void addNamespace(NamespaceBinding nscode) throws XPathException {
		if (localNamespaces == null) {
			localNamespaces = new NamespaceBinding[]{nscode, null, null, null};
		}
		for (int n = 0; n < localNamespaces.length; n++) {
			NamespaceBinding nn = localNamespaces[n];
			if (nn == null) {
				localNamespaces[n] = nscode;
				if (n < localNamespaces.length - 1) {
					localNamespaces[n + 1] = null;
				}
				return;
			}
			if (nn.equals(nscode)) {
				return;
			}
			if ((nn.getPrefix().equals(nscode.getPrefix()))) {
				String prefix = nscode.getPrefix();
				String uri1 = nn.getURI();
				String uri2 = nscode.getURI();
				XPathException err = new XPathException(
					"Cannot create two namespace nodes with the same prefix mapped to different URIs (prefix=" +
						(prefix.length() == 0 ? "\"\"" : prefix) + ", URI=" +
						(uri1.length() == 0 ? "\"\"" : uri1) + ", URI=" +
						(uri2.length() == 0 ? "\"\"" : uri2) + ')');
				err.setErrorCode("XTDE0430");
				throw err;
			}
		}
		NamespaceBinding[] n2 = new NamespaceBinding[localNamespaces.length * 2 + 2];
		System.arraycopy(localNamespaces, 0, n2, 0, localNamespaces.length);
		n2[localNamespaces.length] = nscode;
		n2[localNamespaces.length + 1] = null;
		localNamespaces = n2;
	}

    /**
     * Get the namespaces locally declared on this element
     * @return an array of namespace codes
     */
    public NamespaceBinding[] getLocalNamespaces() {
        if (localNamespaces == null) {
            return NamespaceBinding.EMPTY_ARRAY;
        }
        return localNamespaces;
    }

    /**
     * Add an attribute to the element node
     * @param att the attribute to be added
     * @throws XPathException in the event of a dynamic error, for example a duplicate attribute in XQuery
     */
	public void addAttribute(NodeInfo att) throws XPathException {
		if (attributes == null) {
			attributes = new ArrayList<>(4);
		}
		int fp = att.getFingerprint();
		for (int a = 0; a < attributes.size(); a++) {
			int fp2 = attributes.get(a).getFingerprint();
			if (fp == fp2) {
				if (pipe.getHostLanguage() == Configuration.XQUERY) {
					// In XQuery, duplicate attributes are an error
					XPathException err = new XPathException(
						"Cannot create an element having two attributes with the same name: " +
							Err.wrap(att.getDisplayName(), Err.ATTRIBUTE));
					err.setErrorCode("XQDY0025");
					if (locationId != -1) {
						err.setLocator(new ExpressionLocation(pipe.getLocationProvider(), locationId));
					}
					throw err;
				} else {
					// In XSLT, the last attribute to be added wins
					attributes.set(a, att);
					return;
				}
			}
		}
		attributes.add(att);
	}

    /**
     * Ask whether the element has any attributes
     * @return true if the element has one or more attributes
     */
    public boolean hasAttributes() {
        return attributes != null && !attributes.isEmpty();
    }

    /**
     * Ask how may attributes the element has
     * @return the number of attributes that the element has
     */
    public int getAttributeCount() {
        return attributes == null ? 0 : attributes.size();
    }

    /**
     * Get an iterator over the attributes of this element
     * @return an iterator which delivers NodeInfo objects representing the attributes of this element
     */
    public Iterator iterateAttributes() {
        if (attributes == null) {
            return Collections.emptyList().iterator();
        } else {
            return attributes.iterator();
        }
    }

    /**
     * Get the n'th attribute if there is one
     * @param index the index of the attributes, starting from zero
     * @return a NodeInfo representing the attribute, or null if there is no such attribute
     */
    public NodeInfo getAttribute(int index) {
        if (attributes == null) {
            return null;
        } else {
            return attributes.get(index);
        }
    }

    /**
     * Perform namespace fixup. This is done after all the attributes and explicit namespaces have been added.
     * Namespace fixup ensures that a namespace declaration is present for the element name and for every
     * attribute name, and that the prefixes of the element and each attribute are consistent with the declared
     * namespaces, changing any prefixes in the event of a conflict.
     */
    public void namespaceFixup() {
        elementName = fixup(elementName, 0);
        if (attributes != null) {
            for (int a=0; a<attributes.size(); a++) {
                NodeInfo oldAtt = attributes.get(a);
                NodeName oldCode = new NameOfNode(oldAtt);
                NodeName newCode = fixup(new NameOfNode(oldAtt), a);
                if (oldCode != newCode) {
                    Orphan att = new Orphan(oldAtt.getConfiguration());
                    att.setNodeKind(Type.ATTRIBUTE);
                    att.setNodeName(newCode);
                    att.setStringValue(oldAtt.getStringValue());
                    att.setTypeAnnotation(oldAtt.getSchemaType());
                    att.setSystemId(oldAtt.getSystemId());
                    attributes.set(a, att);
                }
            }
        }
    }

	private NodeName fixup(NodeName nameCode, int seq) {
		String prefix = nameCode.getPrefix();
		String uri = nameCode.getURI();
		NamespaceBinding nsBinding = nameCode.getNamespaceBinding();
		if (prefix.length() == 0 && uri.length() == 0) {
			return nameCode;
		}
		if (localNamespaces != null) {
			for (int n = 0; n < localNamespaces.length; n++) {
				NamespaceBinding nn = localNamespaces[n];
				if (nn == null) {
					break;
				}
				if ((prefix.equals(nn.getPrefix()))) {
					if (uri.equals(nn.getURI())) {
						return nameCode;
					}
					// Same as an existing prefix, but mapped to a different URI: we need to choose a new
					// prefix
					String local = nameCode.getLocalPart();
					String prefix2 = prefix + '_' + seq;
					FingerprintedQName newCode = new FingerprintedQName(prefix2, uri, local);
					return fixup(newCode, seq);
				}
			}
		}
		// Namespace declaration not found: we need to add it
		try {
			addNamespace(nsBinding);
		} catch (XPathException err) {
			throw new AssertionError(err);
		}
		return nameCode;
	}

    /**
     * Strip type annotations from the element and its attributes
     */
	public void stripTypeAnnotations() {
		setTypeCode(Untyped.getInstance());
		if (attributes != null) {
			for (int i = 0; i < attributes.size(); i++) {
				NodeInfo att = attributes.get(i);
				if (!BuiltInAtomicType.UNTYPED_ATOMIC.equals(att.getSchemaType())) {
					Orphan o = new Orphan(att.getConfiguration());
					o.setNodeKind(Type.ATTRIBUTE);
					o.setNodeName(new NameOfNode(att));
					o.setStringValue(att.getStringValue());
					o.setSystemId(att.getSystemId());
					o.setTypeAnnotation(BuiltInAtomicType.UNTYPED_ATOMIC);
					attributes.set(i, o);
				}
			}
		}
	}

    /**
     * Get the PipelineConfiguration
     * @return the PipelineConfiguration
     */
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Set the location associated with the event
     * @param locationId a location identifier, to be interpreted by the locationProvider
     * associated with the PipelineConfiguration
     */
    public void setLocationId(int locationId) {
        this.locationId = locationId;
    }

    /**
     * Get the location associated with the event
     * @return a location identifier, to be interpreted by the locationProvider
     * associated with the PipelineConfiguration, or -1 if none is available
     */
    public int getLocationId() {
        return locationId;
    }
}
