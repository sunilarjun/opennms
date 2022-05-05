/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.snmp.snmp4j;

import static org.opennms.protocols.snmp.SnmpParameters.defaultEncoder;

import java.nio.ByteBuffer;
import org.snmp4j.smi.Opaque;
import org.opennms.protocols.snmp.asn1.AsnDecodingException;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Variable;

/**
 * This class is used to decode Opaque encoded Stings and covert to known simple types
 */
public class OpaqueConverter {
    
    
    private static final byte ID_BITTS = 0b00011111;    
    
    /**
     * Tries to decode opaque wrapped values when possible.
     * @param original original value to be converted
     * @return converted value whenn possible or the original one
     */
    public static Variable substituteOpaqueData(final Opaque original) {
        
        byte[] data = original.getValue();
        
        if(original == null || original.length() == 0) {
            return original;
        }
        
        //Opaque type is double wrapped. The first "envelope" must contain a OctetString with data
        // Any class -> bits 8 and 7 are any; Simple type -> bit 6 = 0; bits 5..1 -> type OCTET STRING == 0b00100
        byte firstOfOpaque = data[0];
        if ((firstOfOpaque & 0b00111111) == 0b00000100) {
            try {
                //get data length
                Object[] wrapedDataLengths = defaultEncoder.parseLength(data, 1);
                int octStrOffset = (Integer) wrapedDataLengths[0];
                int octStrLength = (Integer) wrapedDataLengths[1];

                if (octStrOffset + octStrLength != data.length) {
                    return original;
                }
                
                byte firstOfOctetStr = data[octStrOffset];
                
                //Wrapped data must be of a simple type, otherwise return
                if((0b00100000 & firstOfOctetStr) == 1) {
                    return original;
                }
                
                int wrappedDataType;
                int wrappedDataOffset;
                int wrappedDataLength;
                if ((firstOfOctetStr & ID_BITTS) != ID_BITTS) {
                    //simple type
                    wrappedDataType = firstOfOctetStr & ID_BITTS;
                    wrappedDataOffset = octStrOffset + 1;
                } else {
                    //extendet type
                    byte secondOfOctetStr = data[octStrOffset+1];
                    if ((secondOfOctetStr & 0x80) == 0) {
                        //ttpe in first extended byte
                        wrappedDataType = secondOfOctetStr;
                        wrappedDataOffset = octStrOffset + 2;
                    } else {
                        //too big type. not supported jet
                        return original;
                    }
                }
                
                Object[] contentDataLengths = defaultEncoder.parseLength(data, wrappedDataOffset);
                int finalDataOffset =  (Integer) contentDataLengths[0];
                int finalDataLength = (Integer) contentDataLengths[1];
                
                switch (wrappedDataType) {
                    case 120:
                        if (finalDataLength == 4 ) {
                            float floatValue = Float.intBitsToFloat(ByteBuffer.wrap(data, finalDataOffset, finalDataLength).getInt());
                            return new OctetString(Float.toString(floatValue));
                        }
                        break;
                    default:
                        break;
                }
            
            } catch (AsnDecodingException|ClassCastException ex) {
                System.out.println("");
            }
            
            return original;
        }

        return original;
    }

    
}
