////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.KeyManager;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
  * An object representing the collection of documents handled during
  * a single transformation.
  *
  * <p>The function of allocating document numbers is performed
  * by the DocumentNumberAllocator in the Configuration, not by the DocumentPool. This has a
  * number of effects: in particular it allows operations involving multiple
  * documents (such as generateId() and document()) to occur in a free-standing
  * XPath environment.</p>
  */

public final class DocumentPool implements Serializable {

    // The document pool ensures that the document()
    // function, when called twice with the same URI, returns the same document
    // each time. For this purpose we use a hashtable from
    // URI to DocumentInfo object.

    /*@NotNull*/ private Map<DocumentURI, DocumentInfo> documentNameMap = new HashMap<DocumentURI, DocumentInfo>(10);


    // The set of documents known to be unavailable. These documents must remain
    // unavailable for the duration of a transformation or query!

    /*@NotNull*/ private Set<DocumentURI> unavailableDocuments = new HashSet<DocumentURI>(10);

    /**
    * Add a document to the pool
    * @param doc The DocumentInfo for the document in question
    * @param uri The document-uri property of the document.
    */

    public synchronized void add(DocumentInfo doc, /*@Nullable*/ String uri) {
        if (uri!=null) {
            documentNameMap.put(new DocumentURI(uri), doc);
        }
    }

    /**
    * Add a document to the pool
    * @param doc The DocumentInfo for the document in question
    * @param uri The document-uri property of the document.
    */

    public synchronized void add(DocumentInfo doc, /*@Nullable*/ DocumentURI uri) {
        if (uri!=null) {
            documentNameMap.put(uri, doc);
        }
    }

    /**
    * Get the document with a given document-uri
    * @param uri The document-uri property of the document.
    * @return the DocumentInfo with the given document-uri property if it exists,
    * or null if it is not found.
    */

    public synchronized DocumentInfo find(String uri) {
        return documentNameMap.get(new DocumentURI(uri));
    }

    /**
    * Get the document with a given document-uri
    * @param uri The document-uri property of the document.
    * @return the DocumentInfo with the given document-uri property if it exists,
    * or null if it is not found.
    */

    public synchronized DocumentInfo find(DocumentURI uri) {
        return documentNameMap.get(uri);
    }


    /**
     * Get the URI for a given document node, if it is present in the pool. This supports the
     * document-uri() function.
     * @param doc The document node
     * @return The uri of the document node, if present in the pool, or the systemId of the document node otherwise
     */

    /*@Nullable*/ public synchronized String getDocumentURI(NodeInfo doc) {
        for (DocumentURI uri : documentNameMap.keySet()) {
            DocumentInfo found = find(uri);
            if (found == null) {
                continue; // can happen when discard-document() is used concurrently
            }
            if (found.isSameNodeInfo(doc)) {
                return uri.toString();
            }
        }
        return null;
    }

    /**
     * Determine whether a given document is present in the pool
     * @param doc the document being sought
     * @return true if the document is present, false otherwise
     */

    public synchronized boolean contains(DocumentInfo doc) {
        // relies on "equals" for nodes comparing node identity
        return documentNameMap.values().contains(doc);
    }

    /**
     * Release a document from the document pool. This means that if the same document is
     * loaded again later, the source will need to be re-parsed, and nodes will get new identities.
     * @param doc the document to be discarded from the pool
     * @return the document supplied in the doc parameter
     */

    public synchronized DocumentInfo discard(DocumentInfo doc) {
        for (Map.Entry<DocumentURI, DocumentInfo> e : documentNameMap.entrySet()) {
            DocumentURI name = e.getKey();
            DocumentInfo entry = e.getValue();
            if (entry.isSameNodeInfo(doc)) {
                documentNameMap.remove(name);
                return doc;
            }
        }
        return doc;
    }

    /**
     * Release all indexs for documents in this pool held by the KeyManager
     * @param keyManager the keymanager from which indexes are to be released
     */

    public void discardIndexes(/*@NotNull*/ KeyManager keyManager) {
        for (DocumentInfo doc : documentNameMap.values()) {
            keyManager.clearDocumentIndexes(doc);
        }
    }

    /**
     * Add a document URI to the set of URIs known to be unavailable (because doc-available() has returned
     * false
     * @param uri the URI of the unavailable document
     */

    public void markUnavailable(DocumentURI uri) {
        unavailableDocuments.add(uri);
    }

    /**
     * Ask whether a document URI is in the set of URIs known to be unavailable, because doc-available()
     * has been previously called and has returned false
     */

    public boolean isMarkedUnavailable(DocumentURI uri) {
        return unavailableDocuments.contains(uri);
    }

}

