package com.albion.radar.parser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Photon UDP Packet Parser
 *
 * Parses the outer envelope of Photon UDP packets
 * and extracts the inner Protocol18 messages
 */
public class PhotonPacketParser {

    // Photon message types
    public static final byte MESSAGE_TYPE_INIT = 0;
    public static final byte MESSAGE_TYPE_OPERATION = 1;
    public static final byte MESSAGE_TYPE_EVENT = 2;
    public static final byte MESSAGE_TYPE_OPERATION_RESPONSE = 3;

    // Photon header flags
    public static final int HEADER_MAGIC_BYTE = 0xF3;  // Photon signature
    public static final int HEADER_LENGTH = 12;

    private byte[] packetData;

    public PhotonPacketParser(byte[] packetData) {
        this.packetData = packetData;
    }

    /**
     * Parse a Photon UDP packet and return a list of messages
     */
    public List<PhotonMessage> parse() throws PhotonParseException {
        List<PhotonMessage> messages = new ArrayList<>();

        if (packetData == null || packetData.length < HEADER_LENGTH) {
            throw new PhotonParseException("Packet too short");
        }

        int position = 0;

        // Read magic byte
        int magicByte = packetData[position++] & 0xFF;
        if (magicByte != HEADER_MAGIC_BYTE) {
            throw new PhotonParseException("Invalid magic byte: " + magicByte);
        }

        // Read peer ID (2 bytes, big endian)
        short peerId = ByteBuffer.wrap(packetData, position, 2).order(ByteOrder.BIG_ENDIAN).getShort();
        position += 2;

        // Read flags
        int flags = packetData[position++] & 0xFF;

        // Read command count
        int commandCount = packetData[position++] & 0xFF;

        // Read timestamp
        int timestamp = ByteBuffer.wrap(packetData, position, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        position += 4;

        // Read challenge (4 bytes)
        int challenge = ByteBuffer.wrap(packetData, position, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        position += 4;

        // Parse commands
        for (int i = 0; i < commandCount && position < packetData.length; i++) {
            try {
                PhotonMessage message = parseCommand(position);
                if (message != null) {
                    messages.add(message);
                    position = message.endPosition;
                } else {
                    break;
                }
            } catch (Exception e) {
                // Skip malformed command
                break;
            }
        }

        return messages;
    }

    /**
     * Parse a single command from the packet
     */
    private PhotonMessage parseCommand(int startPos) {
        int position = startPos;

        if (position >= packetData.length) return null;

        // Read command type
        int commandType = packetData[position++] & 0xFF;

        // Read channel ID
        int channelId = packetData[position++] & 0xFF;

        // Read flags
        int flags = packetData[position++] & 0xFF;

        // Read reserved byte
        position++; // skip reserved

        // Read command length (4 bytes, big endian)
        int commandLength = ByteBuffer.wrap(packetData, position, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        position += 4;

        // Read sequence number (reliable commands)
        int sequenceNumber = -1;
        if ((flags & 0x01) != 0) { // Reliable flag
            sequenceNumber = ByteBuffer.wrap(packetData, position, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            position += 4;
        }

        // Calculate payload length
        int headerSize = 8 + ((flags & 0x01) != 0 ? 4 : 0);
        int payloadLength = commandLength - headerSize;

        if (payloadLength <= 0 || position + payloadLength > packetData.length) {
            return null;
        }

        // Extract payload
        byte[] payload = new byte[payloadLength];
        System.arraycopy(packetData, position, payload, 0, payloadLength);
        position += payloadLength;

        PhotonMessage message = new PhotonMessage();
        message.commandType = commandType;
        message.channelId = channelId;
        message.flags = flags;
        message.sequenceNumber = sequenceNumber;
        message.payload = payload;
        message.endPosition = position;

        return message;
    }

    /**
     * Parse the payload of a Photon message using Protocol18
     */
    public static Object parsePayload(byte[] payload) {
        if (payload == null || payload.length == 0) return null;

        try {
            Protocol18Parser parser = new Protocol18Parser(payload);
            return parser.readValue();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse event data from payload
     */
    public static Protocol18Parser.EventData parseEvent(byte[] payload) {
        if (payload == null || payload.length == 0) return null;

        try {
            Protocol18Parser parser = new Protocol18Parser(payload);

            // Check message type
            byte messageType = parser.readByte();

            if (messageType == MESSAGE_TYPE_EVENT) {
                return parser.readEventData();
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }

        return null;
    }

    /**
     * Exception class for parsing errors
     */
    public static class PhotonParseException extends Exception {
        public PhotonParseException(String message) {
            super(message);
        }
    }

    /**
     * Represents a single Photon message
     */
    public static class PhotonMessage {
        public int commandType;
        public int channelId;
        public int flags;
        public int sequenceNumber;
        public byte[] payload;
        public int endPosition;

        // Command type constants
        public static final int TYPE_CONNECT = 1;
        public static final int TYPE_VERIFY_CONNECT = 2;
        public static final int TYPE_DISCONNECT = 3;
        public static final int TYPE_PING = 4;
        public static final int TYPE_SEND_RELIABLE = 5;
        public static final int TYPE_SEND_UNRELIABLE = 6;
        public static final int TYPE_SEND_FRAGMENT = 7;
        public static final int TYPE_ACK = 8;
    }
}
