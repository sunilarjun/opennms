/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit4TestClass.java to edit this template
 */
package org.opennms.netmgt.snmp.snmp4j;

import java.io.IOException;
import java.util.Objects;
import org.junit.Test;
import static org.junit.Assert.*;
import org.snmp4j.smi.Opaque;
import org.snmp4j.smi.Variable;

/**
 *
 * @author dmitri
 */
public class OpaqueConverterTest {
    

     private static byte[] hexStringToByteArray(String s) {
        Objects.requireNonNull(s);
        
        int len = s.length();
        if ((len & 1) == 1) {
            throw new IllegalArgumentException("Require a sting with even length");
        }
        byte[] data = new byte[len / 2];
        int hi = 0;
        int i = 0;
        for (char c : s.toCharArray()) {
            int digit = Character.digit(c, 16);
            if (digit == -1) {
                throw new IllegalArgumentException("HEX-String expected");
            }
            if((i & 1) == 0) {
                hi = digit<<4;
            } else {
                data[i >> 1] = (byte)(hi + digit);
            }
            i++;
        }
        return data;
    }   

    
    
    
    public OpaqueConverterTest() {
    }

    /**
     * Test of substituteOpaqueData method, of class OpaqueConverter.
     */
    @Test
    public void testSubstituteOpaqueData() throws IOException {
        System.out.println("substituteOpaqueData");
        Opaque original = new Opaque(hexStringToByteArray("44079f780442f60000"));

        Variable result = OpaqueConverter.substituteOpaqueData(original);
        assertEquals("123.0", result.toString());
    }
    
}
