////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SavedNamespaceContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.DecimalSymbols;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * XSLT 2.0 implementation of format-number() function - removes the dependence on the JDK.
 */

public class FormatNumber extends SystemFunctionCall implements Callable {

	/*@Nullable*/ private NamespaceResolver nsContext = null;
	// held only if the third argument is present, and its value is not known statically

	private DecimalFormatManager decimalFormatManager = null;
	// held only if the decimalFormatSymbols cannot be determined statically

	private DecimalSymbols decimalFormatSymbols = null;
	// held only if the decimal format to use can be determined statically

	private transient String picture = null;
	// held transiently at compile time if the picture is known statically

	private SubPicture[] subPictures = null;
	// held if the picture is known statically

	private transient boolean checked = false;
	// the second time checkArguments is called, it's a global check so the static context is inaccurate

    private NameChecker nameChecker = Name10Checker.getInstance();

    private boolean is30;

    /**
     * Bind aspects of the static context on which the particular function depends
     *
     * @param env the static context of the function call
     */
    @Override
    public void bindStaticContext(StaticContext env) throws XPathException {
        if (checked) {
			return;
		}
		checked = true;
        nsContext = new SavedNamespaceContext(env.getNamespaceResolver());
        nameChecker = env.getConfiguration().getNameChecker();
		decimalFormatManager = env.getDecimalFormatManager();
		if (decimalFormatManager == null) {
			// create a decimal format manager which will allow a "default default" format only
			decimalFormatManager = new DecimalFormatManager();
		}
        decimalFormatSymbols = decimalFormatManager.getDefaultDecimalFormat();
        is30 = env.getXPathLanguageLevel().equals(DecimalValue.THREE);
    }

    public void checkArguments(ExpressionVisitor visitor) throws XPathException {
        super.checkArguments(visitor);
		if (argument[1] instanceof StringLiteral) {
			// picture is known statically - optimize for this common case
			picture = ((StringLiteral)argument[1]).getStringValue();
		}
		if (argument.length==3) {
			if (argument[2] instanceof StringLiteral) {
				// common case, decimal format name is supplied as a string literal

				String lexicalName = ((StringLiteral)argument[2]).getStringValue();

				StructuredQName qName;
				try {
                    boolean is30 = visitor.getExecutable().isAllowXPath30();
					qName = StructuredQName.fromLexicalQName(lexicalName, false, is30, nameChecker, nsContext);
				} catch (XPathException e) {
					XPathException se = new XPathException("Invalid decimal format name. " + e.getMessage());
					se.setErrorCode("FODF1280");
					throw se;
				}

				decimalFormatSymbols = decimalFormatManager.getNamedDecimalFormat(qName);
				if (decimalFormatSymbols == null) {
					XPathException se = new XPathException("Unknown decimal format name " + lexicalName);
					se.setErrorCode("FODF1280");
					throw se;
				}
			} else {
                decimalFormatSymbols = null; // meaning not known statically
            }
		}
    }

	/**
	 * Analyze a picture string into two sub-pictures.
	 * @param picture the picture as written (possibly two subpictures separated by a semicolon)
	 * @param dfs the decimal format symbols
	 * @return an array of two sub-pictures, the positive and the negative sub-pictures respectively.
	 * If there is only one sub-picture, the second one is null.
     * @throws XPathException if the picture is invalid
	 */

	private SubPicture[] getSubPictures(String picture, DecimalSymbols dfs) throws XPathException {
		int[] picture4 = StringValue.expand(picture);
		SubPicture[] pics = new SubPicture[2];
		if (picture4.length==0) {
			XPathException err = new XPathException("format-number() picture is zero-length");
			err.setErrorCode("FODF1310");
			throw err;
		}
		int sep = -1;
		for (int c=0; c<picture4.length; c++) {
			if (picture4[c] == dfs.getPatternSeparator()) {
				if (c==0) {
					grumble("first subpicture is zero-length");
				} else if (sep >= 0) {
					grumble("more than one pattern separator");
				} else if (sep == picture4.length-1) {
					grumble("second subpicture is zero-length");
				}
				sep = c;
			}
		}

		if (sep<0) {
			pics[0] = new SubPicture(picture4, dfs);
			pics[1] = null;
		} else {
			int[] pic0 = new int[sep];
			System.arraycopy(picture4, 0, pic0, 0, sep);
			int[] pic1 = new int[picture4.length - sep - 1];
			System.arraycopy(picture4, sep+1, pic1, 0, picture4.length - sep - 1);
			pics[0] = new SubPicture(pic0, dfs);
			pics[1] = new SubPicture(pic1, dfs);
		}
		return pics;
	}

	/**
	 * preEvaluate: this method suppresses compile-time evaluation by doing nothing.
	 * We can't evaluate early because we don't have access to the DecimalFormatManager.
	 * @param visitor the expression visitor
	 */

	public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
		return this;
	}

	/**
	 * Copy an expression. This makes a deep copy.
	 * @return the copy of the original expression
	 */

	/*@NotNull*/
    public Expression copy() {
		FormatNumber fn = (FormatNumber)super.copy();
		fn.nsContext = nsContext;
		fn.decimalFormatManager = decimalFormatManager;
		fn.decimalFormatSymbols = decimalFormatSymbols;
		fn.picture = picture;
		fn.subPictures = subPictures;
		return fn;
	}

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return super.equals(o) &&
                equalOrNull(nsContext, ((FormatNumber)o).nsContext) &&
                equalOrNull(decimalFormatManager, ((FormatNumber)o).decimalFormatManager) &&
                equalOrNull(decimalFormatSymbols, ((FormatNumber)o).decimalFormatSymbols) &&
                equalOrNull(picture, ((FormatNumber)o).picture) &&
                equalOrNull(subPictures, ((FormatNumber)o).subPictures);

    }

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (nsContext != null ? nsContext.hashCode() : 0);
		result = 31 * result + (decimalFormatManager != null ? decimalFormatManager.hashCode() : 0);
		result = 31 * result + (decimalFormatSymbols != null ? decimalFormatSymbols.hashCode() : 0);
		result = 31 * result + (picture != null ? picture.hashCode() : 0);
		result = 31 * result + Arrays.hashCode(subPictures);
		return result;
	}

	/**
	 * Evaluate in a context where a string is wanted
	 */

	public CharSequence evaluateAsString(XPathContext context) throws XPathException {

		int numArgs = argument.length;
		DecimalSymbols dfs = decimalFormatSymbols;

		AtomicValue av0 = (AtomicValue)argument[0].evaluateItem(context);
		if (av0 == null) {
			av0 = DoubleValue.NaN;
		}
		NumericValue number = (NumericValue)av0;

		if (dfs == null) {
			DecimalFormatManager dfm = decimalFormatManager;
			if (numArgs==2) {
				dfs = dfm.getDefaultDecimalFormat();
			} else {
				// the decimal-format name was given as a run-time expression
                Item arg2 = argument[2].evaluateItem(context);
                if (arg2 == null) {
                    dfs = dfm.getDefaultDecimalFormat();
                } else {
                    String lexicalName = argument[2].evaluateItem(context).getStringValue();
                    StructuredQName qName;
                    try {
                        qName = StructuredQName.fromLexicalQName(lexicalName, false,
                                is30, context.getConfiguration().getNameChecker(), nsContext);
                    } catch (XPathException e) {
                        XPathException err = new XPathException("Invalid decimal format name. " + e.getMessage());
                        err.setErrorCode("FODF1280");
                        err.setLocator(this);
                        err.setXPathContext(context);
                        throw err;
                    }

                    dfs = dfm.getNamedDecimalFormat(qName);
                    if (dfs==null) {
                        XPathException err = new XPathException("format-number function: decimal-format '" + lexicalName + "' is not defined");
                        err.setErrorCode("FODF1280");
                        err.setLocator(this);
                        err.setXPathContext(context);
                        throw err;
                    }
                }
			}
		}
		SubPicture[] pics = subPictures;
		if (pics == null) {
			String format = argument[1].evaluateItem(context).getStringValue();
			pics = getSubPictures(format, dfs);
		}
		return formatNumber(number, pics, dfs);
	}



	/**
	 * Evaluate in a general context
	 */

	public StringValue evaluateItem(XPathContext c) throws XPathException {
		return new StringValue(evaluateAsString(c));
	}

	/**
	 * Format a number, given the two subpictures and the decimal format symbols
	 * @param number the number to be formatted
	 * @param subPictures the negative and positive subPictures
	 * @param dfs the decimal format symbols to be used
	 * @return the formatted number
	 */

	private CharSequence formatNumber(NumericValue number,
			SubPicture[] subPictures,
			DecimalSymbols dfs) {

		NumericValue absN = number;
		SubPicture pic;
		String minusSign = "";
		if (number.signum() < 0) {
			absN = number.negate();
			if (subPictures[1]==null) {
				pic = subPictures[0];
				minusSign = "" + unicodeChar(dfs.getMinusSign());
			} else {
				pic = subPictures[1];
			}
		} else {
			pic = subPictures[0];
		}

		return pic.format(absN, dfs, minusSign);
	}

	private void grumble(String s) throws XPathException {
		dynamicError("format-number picture: " + s, "FODF1310", null);
	}

	/**
	 * Convert a double to a BigDecimal. In general there will be several BigDecimal values that
	 * are equal to the supplied value, and the one we want to choose is the one with fewest non-zero
	 * digits. The algorithm used is rather pragmatic: look for a string of zeroes or nines, try rounding
	 * the number down or up as approriate, then convert the adjusted value to a double to see if it's
	 * equal to the original: if not, use the original value unchanged.
	 * @param value the double to be converted
	 * @param precision 2 for a double, 1 for a float
	 * @return the result of conversion to a double
	 */

	public static BigDecimal adjustToDecimal(double value, int precision) {
		final String zeros = (precision == 1 ? "00000" : "000000000");
		final String nines = (precision == 1 ? "99999" : "999999999");
		BigDecimal initial = BigDecimal.valueOf(value);
		BigDecimal trial = null;
		FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.TINY);
		DecimalValue.decimalToString(initial, fsb);
		String s = fsb.toString();
		int start = (s.charAt(0) == '-' ? 1 : 0);
		int p = s.indexOf('.');
		int i = s.lastIndexOf(zeros);
		if (i > 0) {
			if (p < 0 || i < p) {
				// we're in the integer part
				// try replacing all following digits with zeros and seeing if we get the same double back
				FastStringBuffer sb = new FastStringBuffer(s.length());
				sb.append(s.substring(0, i));
				for (int n=i; n<s.length(); n++) {
					sb.append(s.charAt(n)=='.' ? '.' : '0');
				}
				trial = new BigDecimal(sb.toString());
			} else {
				// we're in the fractional part
				// try truncating the number before the zeros and seeing if we get the same double back
				trial = new BigDecimal(s.substring(0, i));

			}
		} else {
			i = s.indexOf(nines);
			if (i >= 0) {
				if (i == start) {
					// number starts with 99999... or -99999. Try rounding up to 100000.. or -100000...
					FastStringBuffer sb = new FastStringBuffer(s.length() + 1);
					if (start == 1) {
						sb.append('-');
					}
					sb.append('1');
					for (int n=start; n<s.length(); n++) {
						sb.append(s.charAt(n)=='.' ? '.' : '0');
					}
					trial = new BigDecimal(sb.toString());
				} else {
					// try rounding up
					while (i >= 0 && (s.charAt(i) == '9' || s.charAt(i) == '.')) {
						i--;
					}
					if (i < 0 || s.charAt(i) == '-') {
						return initial;     // can't happen: we've already handled numbers starting 99999..
					} else if (p < 0 || i < p) {
						// we're in the integer part
						FastStringBuffer sb = new FastStringBuffer(s.length());
						sb.append(s.substring(0, i));
						sb.append((char)((int)s.charAt(i) + 1));
						for (int n=i; n<s.length(); n++) {
							sb.append(s.charAt(n)=='.' ? '.' : '0');
						}
						trial = new BigDecimal(sb.toString());
					} else {
						// we're in the fractional part - can ignore following digits
						String s2 = s.substring(0, i) + (char)((int)s.charAt(i) + 1);
						trial = new BigDecimal(s2);
					}
				}
			}
		}
		if (trial != null && (precision==1 ? trial.floatValue() == value : trial.doubleValue() == value)) {
			return trial;
		} else {
			return initial;
		}
	}


	/**
	 * Inner class to represent one sub-picture (the negative or positive subpicture)
	 */

	private class SubPicture implements Serializable {

		int minWholePartSize = 0;
		int maxWholePartSize = 0;
		int minFractionPartSize = 0;
		int maxFractionPartSize = 0;
		boolean isPercent = false;
		boolean isPerMille = false;
		String prefix = "";
		String suffix = "";
		int[] wholePartGroupingPositions = null;
		int[] fractionalPartGroupingPositions = null;

		public SubPicture(int[] pic, DecimalSymbols dfs) throws XPathException {

			final int percentSign = dfs.getPercent();
			final int perMilleSign = dfs.getPerMille();
			final int decimalSeparator = dfs.getDecimalSeparator();
			final int groupingSeparator = dfs.getGroupingSeparator();
			final int digitSign = dfs.getDigit();
			final int zeroDigit = dfs.getZeroDigit();

			List<Integer> wholePartPositions = null;
			List<Integer> fractionalPartPositions = null;

			boolean foundDigit = false;
			boolean foundDecimalSeparator = false;
            for (int ch : pic) {
                if (ch == digitSign || ch == zeroDigit || (is30 && isInDigitFamily(ch, zeroDigit))) {
                    foundDigit = true;
                    break;
                }
            }
			if (!foundDigit) {
				grumble("subpicture contains no digit or zero-digit sign");
			}

			int phase = 0;
			// phase = 0: passive characters at start
			// phase = 1: digit signs in whole part
			// phase = 2: zero-digit signs in whole part
			// phase = 3: zero-digit signs in fractional part
			// phase = 4: digit signs in fractional part
			// phase = 5: passive characters at end

            for (int c : pic) {
                if (c == percentSign || c == perMilleSign) {
                    if (isPercent || isPerMille) {
                        grumble("Cannot have more than one percent or per-mille character in a sub-picture");
                    }
                    isPercent = (c == percentSign);
                    isPerMille = (c == perMilleSign);
                    switch (phase) {
                        case 0:
                            prefix += unicodeChar(c);
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                            phase = 5;
                            suffix += unicodeChar(c);
                            break;
                    }
                } else if (c == digitSign) {
                    switch (phase) {
                        case 0:
                        case 1:
                            phase = 1;
                            maxWholePartSize++;
                            break;
                        case 2:
                            grumble("Digit sign must not appear after a zero-digit sign in the integer part of a sub-picture");
                            break;
                        case 3:
                        case 4:
                            phase = 4;
                            maxFractionPartSize++;
                            break;
                        case 5:
                            grumble("Passive character must not appear between active characters in a sub-picture");
                            break;
                    }
                } else if (c == zeroDigit || (is30 && isInDigitFamily(c, zeroDigit))) {
                    switch (phase) {
                        case 0:
                        case 1:
                        case 2:
                            phase = 2;
                            minWholePartSize++;
                            maxWholePartSize++;
                            break;
                        case 3:
                            minFractionPartSize++;
                            maxFractionPartSize++;
                            break;
                        case 4:
                            grumble("Zero digit sign must not appear after a digit sign in the fractional part of a sub-picture");
                            break;
                        case 5:
                            grumble("Passive character must not appear between active characters in a sub-picture");
                            break;
                    }
                } else if (c == decimalSeparator) {
                    switch (phase) {
                        case 0:
                        case 1:
                        case 2:
                            phase = 3;
                            foundDecimalSeparator = true;
                            break;
                        case 3:
                        case 4:
                        case 5:
                            if (foundDecimalSeparator) {
                                grumble("There must only be one decimal separator in a sub-picture");
                            } else {
                                grumble("Decimal separator cannot come after a character in the suffix");
                            }
                            break;
                    }
                } else if (c == groupingSeparator) {
                    switch (phase) {
                        case 0:
                        case 1:
                        case 2:
                            if (wholePartPositions == null) {
                                wholePartPositions = new ArrayList<Integer>(3);
                            }
                            wholePartPositions.add(maxWholePartSize);
                            // note these are positions from a false offset, they will be corrected later
                            break;
                        case 3:
                        case 4:
                            if (maxFractionPartSize == 0) {
                                grumble("Grouping separator cannot be adjacent to decimal separator");
                            }
                            if (fractionalPartPositions == null) {
                                fractionalPartPositions = new ArrayList<Integer>(3);
                            }
                            fractionalPartPositions.add(maxFractionPartSize);
                            break;
                        case 5:
                            grumble("Grouping separator found in suffix of sub-picture");
                            break;
                    }
                } else {    // passive character found
                    switch (phase) {
                        case 0:
                            prefix += unicodeChar(c);
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                            phase = 5;
                            suffix += unicodeChar(c);
                            break;
                    }
                }
            }

			if (minWholePartSize == 0 && !foundDecimalSeparator) {
				minWholePartSize = 1;
			}

			// System.err.println("minWholePartSize = " + minWholePartSize);
			// System.err.println("maxWholePartSize = " + maxWholePartSize);
			// System.err.println("minFractionPartSize = " + minFractionPartSize);
			// System.err.println("maxFractionPartSize = " + maxFractionPartSize);

			// Sort out the grouping positions

			if (wholePartPositions != null) {
				// convert to positions relative to the decimal separator
				int n = wholePartPositions.size();
				wholePartGroupingPositions = new int[n];
				for (int i=0; i<n; i++) {
					wholePartGroupingPositions[i] =
						maxWholePartSize - wholePartPositions.get(n - i - 1);
				}
				if (n > 1) {
					boolean regular = true;
					int first = wholePartGroupingPositions[0];
					for (int i=1; i<n; i++) {
						if (wholePartGroupingPositions[i] != i * first) {
							regular = false;
							break;
						}
					}
					if (regular) {
						wholePartGroupingPositions = new int[1];
						wholePartGroupingPositions[0] = first;
					}
				}
				if (wholePartGroupingPositions[0] == 0) {
					grumble("Cannot have a grouping separator adjacent to the decimal separator");
				}
			}

			if (fractionalPartPositions != null) {
				int n = fractionalPartPositions.size();
				fractionalPartGroupingPositions = new int[n];
				for (int i=0; i<n; i++) {
					fractionalPartGroupingPositions[i] = fractionalPartPositions.get(i);
				}
			}
		}

		/**
		 * Format a number using this sub-picture
		 * @param value the absolute value of the number to be formatted
		 * @param dfs the decimal format symbols to be used
		 * @param minusSign the representation of a minus sign to be used
		 * @return the formatted number
		 */

		public CharSequence format(NumericValue value, DecimalSymbols dfs, String minusSign) {

			// System.err.println("Formatting " + value);

			if (value.isNaN()) {
				return dfs.getNaN();     // changed by W3C Bugzilla 2712
			}

			if ((value instanceof DoubleValue || value instanceof FloatValue) &&
					Double.isInfinite(value.getDoubleValue())) {
				return minusSign + prefix + dfs.getInfinity() + suffix;
			}

			int multiplier = 1;
			if (isPercent) {
				multiplier = 100;
			} else if (isPerMille) {
				multiplier = 1000;
			}

			if (multiplier != 1) {
				try {
					//value = value.arithmetic(Token.MULT, new Int64Value(multiplier), null);
					value = (NumericValue)ArithmeticExpression.compute(
							value, Calculator.TIMES, new Int64Value(multiplier), null);
				} catch (XPathException e) {
					value = new DoubleValue(value.getDoubleValue() * multiplier);
				}
			}

			FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.TINY);
			if (value instanceof DoubleValue || value instanceof FloatValue) {
				BigDecimal dec = adjustToDecimal(value.getDoubleValue(), 2);
				formatDecimal(dec, sb);

				//formatDouble(value.getDoubleValue(), sb);

			} else if (value instanceof Int64Value || value instanceof BigIntegerValue) {
				formatInteger(value, sb);

			} else if (value instanceof DecimalValue) {
				//noinspection RedundantCast
				formatDecimal(((DecimalValue)value).getDecimalValue(), sb);
			}

			// System.err.println("Justified number: " + sb.toString());

			// Map the digits and decimal point to use the selected characters

			int[] ib = StringValue.expand(sb);
			int ibused = ib.length;
			int point = sb.indexOf('.');
			if (point == -1) {
				point = sb.length();
			} else {
				ib[point] = dfs.getDecimalSeparator();

				// If there is no fractional part, delete the decimal point
				if (maxFractionPartSize == 0) {
					ibused--;
				}
			}

			// Map the digits

			if (dfs.getZeroDigit() != '0') {
				int newZero = dfs.getZeroDigit();
				for (int i=0; i<ibused; i++) {
					int c = ib[i];
					if (c>='0' && c<='9') {
						ib[i] = (c-'0'+newZero);
					}
				}
			}

			// Add the whole-part grouping separators

			if (wholePartGroupingPositions != null) {
				if (wholePartGroupingPositions.length == 1) {
					// grouping separators are at regular positions
					int g = wholePartGroupingPositions[0];
					int p = point - g;
					while (p > 0) {
						ib = insert(ib, ibused++, dfs.getGroupingSeparator(), p);
						//sb.insert(p, unicodeChar(dfs.groupingSeparator));
						p -= g;
					}
				} else {
					// grouping separators are at irregular positions
                    for (int wholePartGroupingPosition : wholePartGroupingPositions) {
                        int p = point - wholePartGroupingPosition;
                        if (p > 0) {
                            ib = insert(ib, ibused++, dfs.getGroupingSeparator(), p);
                            //sb.insert(p, unicodeChar(dfs.groupingSeparator));
                        }
                    }
				}
			}

			// Add the fractional-part grouping separators

			if (fractionalPartGroupingPositions != null) {
				// grouping separators are at irregular positions.
				for (int i=0; i<fractionalPartGroupingPositions.length; i++) {
					int p = point + 1 + fractionalPartGroupingPositions[i] + i;
					if (p < ibused-1) {
						ib = insert(ib, ibused++, dfs.getGroupingSeparator(), p);
						//sb.insert(p, dfs.groupingSeparator);
					} else {
						break;
					}
				}
			}

			// System.err.println("Grouped number: " + sb.toString());

			//sb.insert(0, prefix);
			//sb.insert(0, minusSign);
			//sb.append(suffix);
			FastStringBuffer res = new FastStringBuffer(prefix.length() + minusSign.length() + suffix.length() + ibused);
			res.append(minusSign);
			res.append(prefix);
            for (int i=0; i<ibused; i++) {
                res.appendWideChar(ib[i]);
            }
			res.append(suffix);
			return res;
		}


		/**
		 * Format a number supplied as a decimal
		 * @param dval the decimal value
		 * @param fsb the FastStringBuffer to contain the result
		 */
		private void formatDecimal(BigDecimal dval, FastStringBuffer fsb) {
			dval = dval.setScale(maxFractionPartSize, BigDecimal.ROUND_HALF_EVEN);
			DecimalValue.decimalToString(dval, fsb);

			int point = fsb.indexOf('.');
			int intDigits;
			if (point >= 0) {
				int zz = maxFractionPartSize - minFractionPartSize;
				while (zz>0) {
					if (fsb.charAt(fsb.length()-1) == '0') {
						fsb.setLength(fsb.length()-1);
						zz--;
					} else {
						break;
					}
				}
				intDigits = point;
				if (fsb.charAt(fsb.length()-1) == '.') {
					fsb.setLength(fsb.length()-1);
				}
			} else {
				intDigits = fsb.length();
				if (minFractionPartSize > 0) {
					fsb.append('.');
					for (int i=0; i<minFractionPartSize; i++) {
						fsb.append('0');
					}
				}
			}
			if (minWholePartSize == 0 && intDigits == 1 && fsb.charAt(0) == '0') {
				fsb.removeCharAt(0);
			} else {
				fsb.prependRepeated('0', minWholePartSize - intDigits);
			}
		}

		/**
		 * Format a number supplied as a integer
		 * @param value the integer value
		 * @param fsb the FastStringBuffer to contain the result
		 */

		private void formatInteger(NumericValue value, FastStringBuffer fsb) {
			fsb.append(value.getStringValueCS());
			int leadingZeroes = minWholePartSize - fsb.length();
			fsb.prependRepeated('0', leadingZeroes);
			if (minFractionPartSize != 0) {
				fsb.append('.');
				for (int i=0; i < minFractionPartSize; i++) {
					fsb.append('0');
				}
			}
		}


	}

	/**
	 * Convert a Unicode character (possibly >65536) to a String, using a surrogate pair if necessary
	 * @param ch the Unicode codepoint value
	 * @return a string representing the Unicode codepoint, either a string of one character or a surrogate pair
	 */

	private static CharSequence unicodeChar(int ch) {
		if (ch < 65536) {
			return Character.toString((char) ch);
		} else { 
			// output a surrogate pair
			// To compute the numeric value of the character corresponding to a surrogate
			// pair, use this formula (all numbers are hex):
			// (FirstChar - D800) * 400 + (SecondChar - DC00) + 10000
			ch -= 65536;
			char[] sb = new char[2];
			sb[0] = ((char) ((ch / 1024) + 55296));
			sb[1] = ((char) ((ch % 1024) + 56320));
			return new CharSlice(sb, 0, 2);
		}
	}

	/**
	 * Insert an integer into an array of integers. This may or may not modify the supplied array.
	 * @param array the initial array
	 * @param used the number of items in the initial array that are used
	 * @param value the integer to be inserted
	 * @param position the position of the new integer in the final array
	 * @return the new array, with the new integer inserted
	 */

	private static int[] insert(int[] array, int used, int value, int position) {
		if (used+1 > array.length) {
			int[] a2 = new int[used+10];
			System.arraycopy(array, 0, a2, 0, used);
			array = a2;
		}
        System.arraycopy(array, position, array, position + 1, used - position);
		array[position] = value;
		return array;
	}

	public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
		int numArgs = arguments.length;
		DecimalSymbols dfs = decimalFormatSymbols;

		AtomicValue av0 = (AtomicValue)arguments[0].head();
		if (av0 == null) {
			av0 = DoubleValue.NaN;
		}
		NumericValue number = (NumericValue)av0;

		if (dfs == null) {
			DecimalFormatManager dfm = decimalFormatManager;
			if (numArgs==2) {
				dfs = dfm.getDefaultDecimalFormat();
			} else {
				// the decimal-format name was given as a run-time expression
                Item arg2 = arguments[2].head();
                if (arg2 == null) {
                    dfs = decimalFormatManager.getDefaultDecimalFormat();
                } else {
                    String lexicalName = arguments[2].head().getStringValue();
                    StructuredQName qName;
                    try {
                        boolean is30 = context.getController().getExecutable().isAllowXPath30();
                        qName = StructuredQName.fromLexicalQName(lexicalName, false,
                                is30, context.getConfiguration().getNameChecker(), nsContext);
                    } catch (XPathException e) {
                        XPathException err = new XPathException("Invalid decimal format name. " + e.getMessage());
                        err.setErrorCode("FODF1280");
                        err.setLocator(this);
                        err.setXPathContext(context);
                        throw err;
                    }

                    dfs = dfm.getNamedDecimalFormat(qName);
                    if (dfs==null) {
                        XPathException err = new XPathException("format-number function: decimal-format '" + lexicalName + "' is not defined");
                        err.setErrorCode("FODF1280");
                        err.setLocator(this);
                        err.setXPathContext(context);
                        throw err;
                    }
                }
			}
		}
		SubPicture[] pics = subPictures;
		if (pics == null) {
			String format = arguments[1].head().getStringValue();
			pics = getSubPictures(format, dfs);
		}
		return new StringValue(formatNumber(number, pics, dfs));
	}

    private static boolean isInDigitFamily(int ch, int zeroDigit) {
        return (ch >= zeroDigit && ch <= zeroDigit + 10);
    }
}

