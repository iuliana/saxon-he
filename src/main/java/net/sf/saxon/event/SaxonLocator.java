////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;
import org.xml.sax.Locator;

import javax.xml.transform.SourceLocator;

/**
  * SaxonLocator: this interface exists to unify the SAX Locator and JAXP SourceLocator interfaces,
  * which are identical. It extends both interfaces. Therefore, anything
  * that implements SaxonLocator can be used both in SAX and in JAXP interfaces.
  */

public interface SaxonLocator extends Locator, SourceLocator, LocationProvider {}

