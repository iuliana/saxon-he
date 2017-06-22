////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.Configuration;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.SchemaType;
import org.w3c.dom.TypeInfo;

/**
 * This class implements the DOM TypeInfo interface as a wrapper over the Saxon SchemaType
 * interface.
 */

public class TypeInfoImpl implements TypeInfo {

    private Configuration config;
    private SchemaType schemaType;

    /**
     * Construct a TypeInfo based on a SchemaType
     */

    public TypeInfoImpl(Configuration config, SchemaType type) {
        this.config = config;
        this.schemaType = type;
    }

    /**
     * Get the local name of the type (a system-allocated name if anonymous). Needed to implement the
     * DOM level 3 TypeInfo interface.
     */

    public String getTypeName() {
        return config.getNamePool().getLocalName(schemaType.getNameCode());
    }

    /**
     * Get the namespace name of the type (a system-allocated name if anonymous). Needed to implement the
     * DOM level 3 TypeInfo interface.
     */

    public String getTypeNamespace() {
        return config.getNamePool().getURI(schemaType.getNameCode());
    }

    /**
     * This method returns true if there is a derivation between the reference type definition, that is the TypeInfo
     * on which the method is being called, and the other type definition, that is the one passed as parameters.
     * This method implements the DOM Level 3 TypeInfo interface. It must be called only on a valid type.
     * @param typeNamespaceArg the namespace of the "other" type
     * @param typeNameArg the local name of the "other" type
     * @param derivationMethod the derivation method: zero or more of {@link SchemaType#DERIVATION_RESTRICTION},
     * {@link SchemaType#DERIVATION_EXTENSION}, {@link SchemaType#DERIVATION_LIST}, or {@link SchemaType#DERIVATION_UNION}.
     * Zero means derived by any possible route.
     */

    public boolean isDerivedFrom(String typeNamespaceArg,
                                 String typeNameArg,
                                 int derivationMethod) throws IllegalStateException {
        SchemaType base = schemaType.getBaseType();
        int fingerprint = config.getNamePool().allocate("", typeNamespaceArg, typeNameArg);
        if (derivationMethod==0 || (derivationMethod & schemaType.getDerivationMethod()) != 0) {
            if (base.getFingerprint() == fingerprint) {
                return true;
            } else if (base instanceof AnyType) {
                return false;
            } else {
                return new TypeInfoImpl(config, base).isDerivedFrom(typeNamespaceArg, typeNameArg, derivationMethod);
            }
        }
        return false;
        // Note: if derivationMethod is RESTRICTION, this interpretation requires every step to be derived
        // by restriction. An alternative interpretation is that at least one step must be derived by restriction.
    }

}

