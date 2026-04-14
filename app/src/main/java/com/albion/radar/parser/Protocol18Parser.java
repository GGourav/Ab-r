package com.albion.radar.parser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Protocol18 (GpBinaryV18) Parser for Albion Online
 *
 * Based on Photon Engine GpBinaryV18 specification
 * - Uses varint encoding for integers
 * - Different type codes from Protocol16
 * - Supports optimized zero values and compressed integers
 */
public class Protocol18Parser {

    // GpTypeV18 enum values
    public static final byte TYPE_UNKNOWN = 0;
    public static final byte TYPE_BOOLEAN = 2;
    public static final byte TYPE_BYTE = 3;
    public static final byte TYPE_SHORT = 4;
    public static final byte TYPE_FLOAT = 5;
    public static final byte TYPE_DOUBLE = 6;
    public static final byte TYPE_STRING = 7;
    public static final byte TYPE_NULL = 8;
    public static final byte TYPE_COMPRESSED_INT = 9;
    public static final byte TYPE_COMPRESSED_LONG = 10;
    public static final byte TYPE_INT1 = 11;      // Positive 1-byte int
    public static final byte TYPE_INT1_NEG = 12;  // Negative 1-byte int
    public static final byte TYPE_INT2 = 13;      // Positive 2-byte int
    public static final byte TYPE_INT2_NEG = 14;  // Negative 2-byte int
    public static final byte TYPE_LONG1 = 15;
    public static final byte TYPE_LONG1_NEG = 16;
    public static final byte TYPE_LONG2 = 17;
    public static final byte TYPE_LONG2_NEG = 18;
    public static final byte TYPE_CUSTOM = 19;
    public static final byte TYPE_DICTIONARY = 20;
    public static final byte TYPE_HASHTABLE = 21;
    public static final byte TYPE_OBJECT_ARRAY = 23;
    public static final byte TYPE_OPERATION_REQUEST = 24;
    public static final byte TYPE_OPERATION_RESPONSE = 25;
    public static final byte TYPE_EVENT_DATA = 26;
    public static final byte TYPE_BOOLEAN_FALSE = 27;
    public static final byte TYPE_BOOLEAN_TRUE = 28;
    public static final byte TYPE_SHORT_ZERO = 29;
    public static final byte TYPE_INT_ZERO = 30;
    public static final byte TYPE_LONG_ZERO = 31;
    public static final byte TYPE_FLOAT_ZERO = 32;
    public static final byte TYPE_DOUBLE_ZERO = 33;
    public static final byte TYPE_BYTE_ZERO = 34;
    public static final byte TYPE_ARRAY = 0x40;  // Bitwise OR with base types
    public static final byte TYPE_CUSTOM_TYPE_SLIM = (byte) 0x80;

    private byte[] data;
    private int position;

    public Protocol18Parser(byte[] data) {
        this.data = data;
        this.position = 0;
    }

    /**
     * Decode a varint (variable-length integer)
     * Used for CompressedInt and CompressedLong types
     */
    public long decodeVarint() {
        long result = 0;
        int shift = 0;

        while (position < data.length) {
            byte b = data[position++];
            result |= (long)(b & 0x7F) << shift;

            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }

        return result;
    }

    /**
     * Decode a signed varint (zigzag encoding)
     */
    public long decodeSignedVarint() {
        long value = decodeVarint();
        return (value >>> 1) ^ -(value & 1);
    }

    /**
     * Read a single byte
     */
    public byte readByte() {
        return data[position++];
    }

    /**
     * Read a 16-bit short (little endian)
     */
    public short readShort() {
        short value = (short)((data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8));
        position += 2;
        return value;
    }

    /**
     * Read a 32-bit integer (little endian)
     */
    public int readInt() {
        int value = ByteBuffer.wrap(data, position, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        position += 4;
        return value;
    }

    /**
     * Read a 64-bit long (little endian)
     */
    public long readLong() {
        long value = ByteBuffer.wrap(data, position, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        position += 8;
        return value;
    }

    /**
     * Read a 32-bit float (little endian)
     */
    public float readFloat() {
        float value = ByteBuffer.wrap(data, position, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        position += 4;
        return value;
    }

    /**
     * Read a 64-bit double (little endian)
     */
    public double readDouble() {
        double value = ByteBuffer.wrap(data, position, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
        position += 8;
        return value;
    }

    /**
     * Read a string (length-prefixed)
     */
    public String readString() {
        int length = (int) decodeVarint();
        if (length == 0) {
            return "";
        }
        String str = new String(data, position, length, java.nio.charset.StandardCharsets.UTF_8);
        position += length;
        return str;
    }

    /**
     * Read a byte array of specified length
     */
    public byte[] readBytes(int length) {
        byte[] result = new byte[length];
        System.arraycopy(data, position, result, 0, length);
        position += length;
        return result;
    }

    /**
     * Read a value based on its type code
     */
    public Object readValue() {
        if (position >= data.length) {
            return null;
        }

        byte typeCode = readByte();
        return readValueByType(typeCode);
    }

    /**
     * Read a value given its type code
     */
    public Object readValueByType(byte typeCode) {
        switch (typeCode) {
            case TYPE_NULL:
                return null;

            case TYPE_BOOLEAN_FALSE:
                return false;

            case TYPE_BOOLEAN_TRUE:
                return true;

            case TYPE_BYTE_ZERO:
                return (byte) 0;

            case TYPE_BYTE:
                return readByte();

            case TYPE_SHORT_ZERO:
                return (short) 0;

            case TYPE_SHORT:
                return readShort();

            case TYPE_INT_ZERO:
                return 0;

            case TYPE_INT1:
                return (int) readByte();  // Positive 1-byte int

            case TYPE_INT1_NEG:
                return -(int) readByte();  // Negative 1-byte int

            case TYPE_INT2:
                return (int) readShort();  // Positive 2-byte int

            case TYPE_INT2_NEG:
                return -(int) readShort();  // Negative 2-byte int

            case TYPE_COMPRESSED_INT:
                return (int) decodeSignedVarint();

            case TYPE_LONG_ZERO:
                return 0L;

            case TYPE_LONG1:
                return (long) readByte();

            case TYPE_LONG1_NEG:
                return -(long) readByte();

            case TYPE_LONG2:
                return (long) readShort();

            case TYPE_LONG2_NEG:
                return -(long) readShort();

            case TYPE_COMPRESSED_LONG:
                return decodeSignedVarint();

            case TYPE_FLOAT_ZERO:
                return 0.0f;

            case TYPE_FLOAT:
                return readFloat();

            case TYPE_DOUBLE_ZERO:
                return 0.0d;

            case TYPE_DOUBLE:
                return readDouble();

            case TYPE_STRING:
                return readString();

            case TYPE_HASHTABLE:
                return readHashtable();

            case TYPE_DICTIONARY:
                return readDictionary();

            case TYPE_OBJECT_ARRAY:
                return readObjectArray();

            case TYPE_CUSTOM:
                return readCustomType();

            case TYPE_OPERATION_REQUEST:
                return readOperationRequest();

            case TYPE_OPERATION_RESPONSE:
                return readOperationResponse();

            case TYPE_EVENT_DATA:
                return readEventData();

            default:
                // Check if it's an array type
                if ((typeCode & TYPE_ARRAY) != 0) {
                    return readArray(typeCode);
                }
                // Check if it's a custom type slim
                if ((typeCode & TYPE_CUSTOM_TYPE_SLIM) != 0) {
                    return readCustomTypeSlim(typeCode);
                }
                return null;
        }
    }

    /**
     * Read a Hashtable (Photon's HashMap implementation)
     */
    public Hashtable<Object, Object> readHashtable() {
        int count = (int) decodeVarint();
        Hashtable<Object, Object> hashtable = new Hashtable<>();

        for (int i = 0; i < count; i++) {
            Object key = readValue();
            Object value = readValue();
            hashtable.put(key, value);
        }

        return hashtable;
    }

    /**
     * Read a Dictionary
     */
    public Map<Object, Object> readDictionary() {
        byte keyType = readByte();
        byte valueType = readByte();
        int count = (int) decodeVarint();

        Map<Object, Object> dictionary = new HashMap<>();

        for (int i = 0; i < count; i++) {
            Object key = readValueByType(keyType);
            Object value = readValueByType(valueType);
            dictionary.put(key, value);
        }

        return dictionary;
    }

    /**
     * Read an Object Array
     */
    public Object[] readObjectArray() {
        int count = (int) decodeVarint();
        Object[] array = new Object[count];

        for (int i = 0; i < count; i++) {
            array[i] = readValue();
        }

        return array;
    }

    /**
     * Read a typed array
     */
    public Object readArray(byte typeCode) {
        byte baseType = (byte) (typeCode & ~TYPE_ARRAY);
        int count = (int) decodeVarint();

        switch (baseType) {
            case TYPE_BOOLEAN:
                boolean[] boolArray = new boolean[count];
                for (int i = 0; i < count; i++) {
                    boolArray[i] = (Boolean) readValueByType(TYPE_BOOLEAN);
                }
                return boolArray;

            case TYPE_BYTE:
                return readBytes(count);

            case TYPE_SHORT:
                short[] shortArray = new short[count];
                for (int i = 0; i < count; i++) {
                    shortArray[i] = readShort();
                }
                return shortArray;

            case TYPE_COMPRESSED_INT:
                int[] intArray = new int[count];
                for (int i = 0; i < count; i++) {
                    intArray[i] = (Integer) readValueByType(TYPE_COMPRESSED_INT);
                }
                return intArray;

            case TYPE_FLOAT:
                float[] floatArray = new float[count];
                for (int i = 0; i < count; i++) {
                    floatArray[i] = readFloat();
                }
                return floatArray;

            case TYPE_DOUBLE:
                double[] doubleArray = new double[count];
                for (int i = 0; i < count; i++) {
                    doubleArray[i] = readDouble();
                }
                return doubleArray;

            case TYPE_STRING:
                String[] stringArray = new String[count];
                for (int i = 0; i < count; i++) {
                    stringArray[i] = readString();
                }
                return stringArray;

            default:
                return readObjectArray();
        }
    }

    /**
     * Read a Custom Type
     */
    public Object readCustomType() {
        byte customTypeCode = readByte();
        int length = (int) decodeVarint();
        byte[] customData = readBytes(length);

        // Return a CustomType wrapper for further processing
        return new CustomType(customTypeCode, customData);
    }

    /**
     * Read a Custom Type Slim
     */
    public Object readCustomTypeSlim(byte typeCode) {
        byte customTypeCode = (byte) (typeCode & ~TYPE_CUSTOM_TYPE_SLIM);
        int length = (int) decodeVarint();
        byte[] customData = readBytes(length);

        return new CustomType(customTypeCode, customData);
    }

    /**
     * Read an Operation Request
     */
    public OperationRequest readOperationRequest() {
        byte operationCode = readByte();
        Hashtable<Object, Object> parameters = readHashtable();

        return new OperationRequest(operationCode, parameters);
    }

    /**
     * Read an Operation Response
     */
    public OperationResponse readOperationResponse() {
        byte operationCode = readByte();
        short returnCode = readShort();
        String debugMessage = readString();
        Hashtable<Object, Object> parameters = readHashtable();

        return new OperationResponse(operationCode, returnCode, debugMessage, parameters);
    }

    /**
     * Read an Event Data
     */
    public EventData readEventData() {
        byte eventCode = readByte();
        Hashtable<Object, Object> parameters = readHashtable();

        return new EventData(eventCode, parameters);
    }

    /**
     * Get current position
     */
    public int getPosition() {
        return position;
    }

    /**
     * Set position
     */
    public void setPosition(int pos) {
        this.position = pos;
    }

    /**
     * Get remaining bytes
     */
    public int remaining() {
        return data.length - position;
    }

    /**
     * Check if more data available
     */
    public boolean hasRemaining() {
        return position < data.length;
    }

    // Inner classes for structured data

    public static class CustomType {
        public byte typeCode;
        public byte[] data;

        public CustomType(byte typeCode, byte[] data) {
            this.typeCode = typeCode;
            this.data = data;
        }
    }

    public static class OperationRequest {
        public byte operationCode;
        public Hashtable<Object, Object> parameters;

        public OperationRequest(byte operationCode, Hashtable<Object, Object> parameters) {
            this.operationCode = operationCode;
            this.parameters = parameters;
        }
    }

    public static class OperationResponse {
        public byte operationCode;
        public short returnCode;
        public String debugMessage;
        public Hashtable<Object, Object> parameters;

        public OperationResponse(byte operationCode, short returnCode, String debugMessage, Hashtable<Object, Object> parameters) {
            this.operationCode = operationCode;
            this.returnCode = returnCode;
            this.debugMessage = debugMessage;
            this.parameters = parameters;
        }
    }

    public static class EventData {
        public byte eventCode;
        public Hashtable<Object, Object> parameters;

        public EventData(byte eventCode, Hashtable<Object, Object> parameters) {
            this.eventCode = eventCode;
            this.parameters = parameters;
        }
    }
        }
