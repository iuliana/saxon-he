////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
  * NamespaceReducer is a ProxyReceiver responsible for removing duplicate namespace
  * declarations. It also ensures that an xmlns="" undeclaration is output when
  * necessary. Used on its own, the NamespaceReducer simply eliminates unwanted
  * namespace declarations. It can also be subclassed, in which case the subclass
  * can use the services of the NamespaceReducer to resolve QNames.
  * <p>
  * The NamespaceReducer also validates namespace-sensitive content.
  */

public class NamespaceReducer extends ProxyReceiver implements NamespaceResolver
{
    // We keep track of namespaces to avoid outputting duplicate declarations. The namespaces
    // array holds a list of all namespaces currently declared (organised as pairs of entries,
    // prefix followed by URI). The countStack contains an entry for each element currently open; the
    // value on the stack is an integer giving the number of namespaces added to the main
    // namespace stack by that element.

    private NamespaceBinding[] namespaces = new NamespaceBinding[50];          // all namespace codes currently declared
    private int namespacesSize = 0;                  // all namespaces currently declared
    private int[] countStack = new int[50];
    private int depth = 0;

    // Creating an element does not automatically inherit the namespaces of the containing element.
    // When the DISINHERIT property is set on startElement(), this indicates that the namespaces
    // on that element are not to be automatically inherited by its children. So startElement()
    // stacks a boolean flag indicating whether the children are to disinherit the parent's namespaces.

    private boolean[] disinheritStack = new boolean[50];

    // When a child element does not inherit the namespaces of its parent, it acquires undeclarations
    // to indicate this fact. This array keeps track of the undeclarations that need to be added to the
    // current child element.

    private NamespaceBinding[] pendingUndeclarations = null;

    /**
     * Create a NamespaceReducer
     * @param next the Receiver to which events will be passed after namespace reduction
     */

    public NamespaceReducer(Receiver next) {
        super(next);
    }

    /**
    * startElement. This call removes redundant namespace declarations, and
    * possibly adds an xmlns="" undeclaration.
    */

    public void startElement(NodeName elemName, SchemaType typeCode, int locationId, int properties) throws XPathException {

        nextReceiver.startElement(elemName, typeCode, locationId, properties);

        // If the parent element specified inherit=no, keep a list of namespaces that need to be
        // undeclared

        if ((depth>0 && disinheritStack[depth-1]) || ((properties & ReceiverOptions.REFUSE_NAMESPACE_INHERITANCE) != 0)) {
            pendingUndeclarations = new NamespaceBinding[namespacesSize];
            System.arraycopy(namespaces, 0, pendingUndeclarations, 0, namespacesSize);
        } else {
            pendingUndeclarations = null;
        }

        // Record the current height of the namespace list so it can be reset at endElement time

        countStack[depth] = 0;
        disinheritStack[depth] = (properties & ReceiverOptions.DISINHERIT_NAMESPACES) != 0;
        if (++depth >= countStack.length) {
            int[] newstack = new int[depth*2];
            System.arraycopy(countStack, 0, newstack, 0, depth);
            boolean[] disStack2 = new boolean[depth*2];
            System.arraycopy(disinheritStack, 0, disStack2, 0, depth);
            countStack = newstack;
            disinheritStack = disStack2;
        }


        // Ensure that the element namespace is output, unless this is done
        // automatically by the caller (which is true, for example, for a literal
        // result element).

        if ((properties & ReceiverOptions.NAMESPACE_OK) == 0) {
            namespace(elemName.getNamespaceBinding(), 0);
        }

    }

    /**
     * Output a namespace node (binding)
     * @param namespaceBinding the prefix/uri pair to be output
     * @param properties the properties of the namespace binding
     * @throws XPathException if any downstream error occurs
     */

    public void namespace(NamespaceBinding namespaceBinding, int properties) throws XPathException {

        // Keep the namespace only if it is actually needed

        if (isNeeded(namespaceBinding)) {
            addToStack(namespaceBinding);
            countStack[depth - 1]++;
            nextReceiver.namespace(namespaceBinding, properties);
        }
    }

    /**
     * Determine whether a namespace declaration is needed
     * @param nsBinding the namespace binding
     * @return true if the namespace is needed: that is, if it not the XML namespace, is not a duplicate,
     * and is not a redundant xmlns="".
    */

    private boolean isNeeded(NamespaceBinding nsBinding) {
        if (nsBinding.isXmlNamespace()) {
        		// Ignore the XML namespace
            return false;
        }

        // First cancel any pending undeclaration of this namespace prefix (there may be more than one)

        String prefix = nsBinding.getPrefix();
        if (pendingUndeclarations != null) {
            for (int p=0; p<pendingUndeclarations.length; p++) {
                NamespaceBinding nb = pendingUndeclarations[p];
                if (nb != null && prefix.equals(nb.getPrefix())) {
                    pendingUndeclarations[p] = null;
                    //break;
                }
            }
        }

        for (int i=namespacesSize-1; i>=0; i--) {
        	if (namespaces[i].equals(nsBinding)) {
        		// it's a duplicate so we don't need it
        		return false;
        	}
        	if ((namespaces[i].getPrefix().equals(nsBinding.getPrefix()))) {
        		// same prefix, different URI.
                return true;
            }
        }

        // we need it unless it's a redundant xmlns=""
        return (!nsBinding.isDefaultUndeclaration());
    }

    /**
     * Add a namespace declaration to the stack
     * @param nsBinding the namespace code to be added
    */

    private void addToStack(NamespaceBinding nsBinding) {
		// expand the stack if necessary
        if (namespacesSize+1 >= namespaces.length) {
            NamespaceBinding[] newlist = new NamespaceBinding[namespacesSize*2];
            System.arraycopy(namespaces, 0, newlist, 0, namespacesSize);
            namespaces = newlist;
        }
        namespaces[namespacesSize++] = nsBinding;
    }

    /**
     * startContent: Add any namespace undeclarations needed to stop
     * namespaces being inherited from parent elements
     */

    public void startContent() throws XPathException {

        if (pendingUndeclarations != null) {
            for (NamespaceBinding ns : pendingUndeclarations) {
                if (ns != null) {
                    namespace(new NamespaceBinding(ns.getPrefix(), ""), 0);
                    // relies on the namespace() method to prevent duplicate undeclarations
                }
            }
        }
        pendingUndeclarations = null;
        nextReceiver.startContent();
    }

    /**
    * endElement: Discard the namespaces declared on this element.
    */


    public void endElement () throws XPathException
    {
        if (depth-- == 0) {
            throw new IllegalStateException("Attempt to output end tag with no matching start tag");
        }

        namespacesSize -= countStack[depth];

        nextReceiver.endElement();

    }

    /**
     * Get the URI code corresponding to a given prefix code, by searching the
     * in-scope namespaces. This is a service provided to subclasses.
     * @param prefixCode the 16-bit prefix code required
     * @return the 16-bit URI code, or -1 if the prefix is not found
     */

//    protected short getURICode(short prefixCode) {
//        for (int i=namespacesSize-1; i>=0; i--) {
//        	if ((namespaces[i]>>16) == (prefixCode)) {
//        		return (short)(namespaces[i]&0xffff);
//            }
//        }
//        if (prefixCode == 0) {
//            return 0;   // by default, no prefix means no namespace URI
//        } else {
//            return -1;
//        }
//    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope
     */

    /*@Nullable*/ public String getURIForPrefix(String prefix, boolean useDefault) {
        if ((prefix.length()==0) && !useDefault) {
            return NamespaceConstant.NULL;
        } else if ("xml".equals(prefix)) {
            return NamespaceConstant.XML;
        } else {
            for (int i=namespacesSize-1; i>=0; i--) {
                if ((namespaces[i].getPrefix().equals(prefix))) {
                    return namespaces[i].getURI();
                }
            }
        }
        return (prefix.length()==0 ? NamespaceConstant.NULL : null);
     }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    public Iterator<String> iteratePrefixes() {
        List<String> prefixes = new ArrayList<String>(namespacesSize);
        for (int i=namespacesSize-1; i>=0; i--) {
            String prefix = namespaces[i].getPrefix();
            if (!prefixes.contains(prefix)) {
                prefixes.add(prefix);
            }
        }
        prefixes.add("xml");
        return prefixes.iterator();
    }
}

