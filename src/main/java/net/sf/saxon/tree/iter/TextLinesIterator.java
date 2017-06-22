////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.functions.UnparsedText;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.SourceLocator;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URI;

/**
 * An iterator that iterates over a file line by line. It is abstract because it does not supply the
 * getAnother() method
*/
public abstract class TextLinesIterator implements SequenceIterator<StringValue> {

    protected LineNumberReader reader;
    protected NameChecker checker;
    StringValue current = null;
    int position = 0;
    protected SourceLocator location;
    protected URI uri;

    protected TextLinesIterator() {

    }

    /**
     * Create a TextLinesIterator over a given reader
     * @param reader the reader that reads the file
     * @param checker checks that the characters in the file are legal XML characters
     * @param location the location of the instruction being executed, for diagnostics
     * @param uri the URI of the file being read, for diagnostics
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
     */

    public TextLinesIterator(LineNumberReader reader, SourceLocator location, URI uri, NameChecker checker) throws XPathException {
        this.reader = reader;
        this.location = location;
        this.uri = uri;
        this.checker = checker;
    }

//    public TextLinesIterator(File file, String encoding) throws IOException {
//        this.file = file;
//        this.encoding = encoding;
//        this.reader = new LineNumberReader(new InputStreamReader(new FileInputStream(file), encoding));
//        this.checker = Name11Checker.getInstance();
//    }

    /*@Nullable*/ public StringValue next() throws XPathException {
        if (position < 0) {
            // input already exhausted
            close(); // jwL
            return null;
        }
        try {
            String s = reader.readLine();
            if (s == null) {
                current = null;
                position = -1;
                close(); // jwL
                return null;
            }
            if (position == 0 && s.startsWith("\ufeff")) {
                // remove any BOM found at start of file
                s = s.substring(1);
            }
            checkLine(checker, s);
            current = new StringValue(s);
            position++;
            return current;
        } catch (IOException err) {
            close(); // jwL
            XPathException e = UnparsedText.handleIOError(uri, err, null);
            if (location != null) {
                e.setLocator(location);
            }
            throw e;
//        } catch (Exception err) {
//            XPathException e = new XPathException(err.getMessage(), "XPST0001");
//            if (location != null) {
//                e.setLocator(location);
//            }
//            throw e;
        }
    }

    /**
     * The current line in the file
     * @return returns StringValue: the current line in the file
     */
    /*@Nullable*/ public StringValue current() {
        return current;
    }

    /**
     * The line position currently being read
     * @return returns the current line in the file
     */
    public int position() {
        return position;
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException err) {
            //
        }
    }


    public int getProperties() {
        return 0;
    }

    private void checkLine(/*@NotNull*/ NameChecker checker, /*@NotNull*/ String buffer) throws XPathException {
        for (int c=0; c<buffer.length();) {
            int ch32 = buffer.charAt(c++);
            if (UTF16CharacterSet.isHighSurrogate(ch32)) {
                char low = buffer.charAt(c++);
                ch32 = UTF16CharacterSet.combinePair((char)ch32, low);
            }
            if (!checker.isValidChar(ch32)) {
                XPathException err = new XPathException("The unparsed-text file contains a character that is illegal in XML (line=" +
                        position + " column=" + (c+1) + " value=hex " + Integer.toHexString(ch32) + ')');
                err.setErrorCode("XTDE1190");
                err.setLocator(location);
                throw err;
            }
        }
    }
}

