//
// Copyright (C) 2002 Sortova Consulting Group, Inc.  All rights reserved.
// Parts Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.sortova.com/
//
//
//
// Tab Size = 8
//
// ASN1.java,v 1.1.1.1 2001/11/11 17:27:23 ben Exp
//
//

package org.opennms.protocols.snmp.asn1;

import java.lang.*;

/**
 * Public ASN.1 definitions. See "SNMPv1, SNMPv2, SNMPv3
 * and RMON 1 and 2, 3rd Ed." by William Stallings, 
 * Published by Addision Wesley for more information.
 *
 * @author	<a href="http://www.opennms.org">OpenNMS</a>
 * @author	<a href="mailto:weave@opennms.org>Brian Weaver</a>
 * @version	1.1.1.1
 */
public class ASN1
{
	/**
	 * Basic data type representing TRUE or FALSE.
	 */
	public static final byte BOOLEAN	= (byte)0x01;

	/**
	 * Positive and negative whole numbers, including zero. 
	 */
	public static final byte INTEGER	= (byte)0x02;

	/**
	 * A sequence of zero or more bits
	 */
	public static final byte BITSTRING	= (byte)0x03;

	/**
	 * A sequence of zero or more octets. An octet is an
	 * 8-bit value.
	 */
	public static final byte OCTETSTRING	= (byte)0x04;

	/**
	 * The single value NULL. Commonly used value 
	 * where several alternatives are possible but none apply.
	 */
	public static final byte NULL		= (byte)0x05;

	/**
	 * The set of values associated with information
	 * objects allocated by the standard.
	 */
	public static final byte OBJECTID	= (byte)0x06;

	/**
	 * Defined by referencing a fixed, ordered list of types.
	 * Each value is an ordered list of values, one from each 
	 * component type.
	 */
	public static final byte SEQUENCE	= (byte)0x10;

	/**
	 * Defined by referencing a fixed, unordered list of types,
	 * some of which may be declared optional. Each value is an
	 * unordered list of values, one from each component type.
	 */
	public static final byte SET		= (byte)0x11;

	/**
	 * Generally useful, application-independant types and
	 * construction mechanisms.
	 */
	public static final byte UNIVERSAL	= (byte)0x00;

	/**
	 * Relevant to a particular application. These are defined
	 * in standards other than ASN.1.
	 */
	public static final byte APPLICATION	= (byte)0x40;

	/**
	 * Also relevant to a particular application, but limited
	 * by context
	 */
	public static final byte CONTEXT	= (byte)0x80;

	/**
	 * These are types not covered by any standard but instead
	 * defined by users.
	 */
	public static final byte PRIVATE	= (byte)0xC0;

	/**
	 * A primitive data object.
	 */
	public static final byte PRIMITIVE	= (byte)0x00;
	
	/**
	 * A constructed data object such as a set or sequence.
	 */
	public static final byte CONSTRUCTOR	= (byte)0x20;
}

