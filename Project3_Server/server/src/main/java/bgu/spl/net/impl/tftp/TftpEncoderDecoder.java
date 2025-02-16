package bgu.spl.net.impl.tftp;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.impl.tftp.DecodedMessage.AddedOrDeleted;
import bgu.spl.net.impl.tftp.DecodedMessage.PacketType;

public class TftpEncoderDecoder implements MessageEncoderDecoder<DecodedMessage> {
    DecodedMessage result;
    private byte[] temp_bytes;
    private byte[] message_bytes;
    private int tempLen;
    private int len = 0;

    public TftpEncoderDecoder(){
        this.result = new DecodedMessage();
        this.temp_bytes = new byte[2];
        this.message_bytes = new byte[1 << 10];
        this.tempLen = 0;
        this.len = 0;
    }
    @Override
    public DecodedMessage decodeNextByte(byte nextByte) {
        // Decoding the first 2 bytes as opcode:
        if(result.getOpcode() == null){
            if (tempLen == 0) {
                temp_bytes[0] = nextByte;
                tempLen++;
            }else{
                temp_bytes[1] = nextByte;
                tempLen = 0;
                result.setOpcode(temp_bytes);
                if (result.getPacketType() == PacketType.DIRQ || result.getPacketType() == PacketType.DISC ){ 
                    // If the packet type is DIRQ or DISC - the decoding is finished
                    return init_encoder_decoder(result);
                }
            }
            return null;
        }else{
            PacketType packetType = result.getPacketType();
            if(packetType == PacketType.DATA){
                return decodeDataNextByte(nextByte);
            }else if (packetType == PacketType.ACK){
                return decodeAckNextByte(nextByte);
            }else if (packetType == PacketType.ERROR){
                return decodeErrorNextByte(nextByte);
            }else if (packetType == PacketType.BCAST){
                return decodeBcastNextByte(nextByte);
            }else{ 
                // The case for RRQ, WRQ, LOGRQ, DELRQ - decoding the next bytes as string (the byte zero means end of the packet)
                if(nextByte != (byte)0){
                    pushByte(nextByte);
                    return null;
                }else{
                    result.setMessage(popString());
                    return init_encoder_decoder(result); 
                }
            }
        }
    }

    // Decode for Data Packet
    private DecodedMessage decodeDataNextByte(byte nextByte){
        if(result.getPacketSize() == null){
            // Decoding the packet size:
            if (tempLen == 0) {
                temp_bytes[0] = nextByte;
                tempLen++;
            }else{
                temp_bytes[1] = nextByte;
                tempLen = 0;
                short packetSize = (short)(((short)(temp_bytes[0] & 0xFF)) << 8 | ((short)(temp_bytes[1] & 0xFF)));
                result.setPacketSize(packetSize);
            }
            return null;
        }else if(result.getBlockNumber() == null){
            // Decoding block number:
            if (tempLen == 0) {
                temp_bytes[0] = nextByte;
                tempLen++;
            }else{
                temp_bytes[1] = nextByte;
                tempLen = 0;
                short blockNumber = (short)(((short)(temp_bytes[0] & 0xFF)) << 8 | ((short)(temp_bytes[1] & 0xFF)));
                result.setBlockNumber(blockNumber);
            }
            return null;
        }else{ 
            // Decoding the rest of the packet as the data
            if(len + 1 == result.getPacketSize()){
                pushByte(nextByte);
                byte[] result_bytes = Arrays.copyOfRange(message_bytes, 0, len);
                result.setDataBuffer(result_bytes);
                len = 0;
                return init_encoder_decoder(result); 
            }
            pushByte(nextByte);
            return null;
            
        }
    }

    // Decode for ACK Packet
    private DecodedMessage decodeAckNextByte(byte nextByte){
        // The next 2 bytes are for the block number and then the decoding is done
        if(result.getBlockNumber() == null && tempLen == 0){
                temp_bytes[0] = nextByte;
                tempLen++;
                return null;
        }else{
            temp_bytes[1] = nextByte;
            tempLen = 0;
            short blockNumber = (short)(((short)(temp_bytes[0] & 0xFF)) << 8 | ((short)(temp_bytes[1] & 0xFF)));
            result.setBlockNumber(blockNumber);
            return init_encoder_decoder(result);
        }
            
    }

    //Decode for Error Packet
    private DecodedMessage decodeErrorNextByte(byte nextByte){
        // Decoding error code:
        if(result.getError() == null){
            if (tempLen == 0) {
                temp_bytes[0] = nextByte;
                tempLen++;
            }else{
                temp_bytes[1] = nextByte;
                tempLen = 0;
                short errorCode = (short)(((short)(temp_bytes[0] & 0xFF)) << 8 | ((short)(temp_bytes[1] & 0xFF)));
                result.setError(errorCode);
            }
            return null;
        }else{
            // Decoding the rest of the bytes as the error message
            if(nextByte != 0){
                pushByte(nextByte);
                return null;
            }
            result.setMessage(popString());
            return init_encoder_decoder(result); 
        }
    }

    // Decode BCAST Packet
    private DecodedMessage decodeBcastNextByte(byte nextByte){
        // Decoding Added / Deleted
        if(result.getAddedOrDeleted() == AddedOrDeleted.undefined){
            if (nextByte == 0) {
                result.setAddedOrDeleted(false);
            }else{
                result.setAddedOrDeleted(true);
            }
            return null;
        }else{
            // Decoding the rest of the bytes as the file name
            if(nextByte != 0){
                pushByte(nextByte);
                return null;
            }
            result.setMessage(popString());
            return init_encoder_decoder(result); 
        }
    }

    private String popString() {
        //notice that we explicitly requesting that the string will be decoded from UTF-8
        String result = new String(message_bytes, 0, len, StandardCharsets.UTF_8);
        len = 0;
        return result;
    }

    private void pushByte(byte nextByte) {
        if (len >= message_bytes.length) {
            message_bytes = Arrays.copyOf(message_bytes, len * 2);
        }
        message_bytes[len++] = nextByte;
    }

    
    @Override
    public byte[] encode(DecodedMessage message) {
        int plength = 0;
        byte[] resultenc;
        int index = 0;
    
        if(message.getPacketType()== PacketType.RRQ|| message.getPacketType()==PacketType.WRQ||
        message.getPacketType()== PacketType.LOGRQ|| message.getPacketType()== PacketType.DELRQ){
            // The length should be: 2 + bytes-size(filename in UTF8)+ 1
            byte[] byte_message = (message.getMessage()).getBytes();
            plength = 2 + byte_message.length + 1;
            resultenc = new byte[plength];
            // Encode opcode:
            resultenc = copyToIndex(resultenc,message.getOpcode(),index);
            index += message.getOpcode().length;
            // Encode Message:
            resultenc = copyToIndex(resultenc, byte_message, index);
            // Add 0 as the last byte:
            resultenc[plength - 1] = 0;
            return resultenc;      
        }
        else if(message.getPacketType()== PacketType.DATA){
            plength = 6 + message.getPacketSize();
            resultenc = new byte[plength];
            // Encode opcode:
            resultenc = copyToIndex(resultenc,message.getOpcode(),index);
            index += message.getOpcode().length;
            // Encode packetsize:
            byte[] bpacketSize = new byte[]{(byte) (message.getPacketSize() >> 8), (byte) (message.getPacketSize()& 0xff)};
            resultenc = copyToIndex(resultenc,bpacketSize,index);
            index += bpacketSize.length;
            // Encode blocknumber:
            byte[] bBlockNumber = new byte[]{(byte) (message.getBlockNumber() >> 8), (byte) (message.getBlockNumber()& 0xff)};
            resultenc = copyToIndex(resultenc,bBlockNumber,index);
            index += bBlockNumber.length;
            // Encode data:
            ByteBuffer byte_buffer = message.getDataBuffer();
            byte[] data_bytes = new byte[byte_buffer.remaining()] ;
            byte_buffer.get(data_bytes);
            resultenc = copyToIndex(resultenc,data_bytes,index);
            return resultenc;
        }
        else if(message.getPacketType()== PacketType.ACK){
            plength = 4;
            resultenc = new byte[plength];
            // Encode opcode:
            resultenc = copyToIndex(resultenc,message.getOpcode(),index);
            index += message.getOpcode().length;
            // Encode blocknumber:
            byte[] bBlockNumber = new byte[]{(byte) (message.getBlockNumber() >> 8), (byte) (message.getBlockNumber()& 0xff)};
            resultenc = copyToIndex(resultenc,bBlockNumber,index);
            return resultenc;  
        }
        else if(message.getPacketType()== PacketType.ERROR){    
            // Encode Error Message:
            byte[] bErrorMsg = (message.getMessage()).getBytes();
            plength = 4 + bErrorMsg.length + 1;
            resultenc = new byte[plength];
            // Encode Opcode:
            resultenc = copyToIndex(resultenc,message.getOpcode(),index);
            index += message.getOpcode().length;
            // Encode Error Code:
            byte[] bErrorCode = new byte[]{(byte) (message.getError() >> 8), (byte) (message.getError()& 0xff)};
            resultenc = copyToIndex(resultenc, bErrorCode, index);
            index += bErrorCode.length;
            // Encode Error Message:
            resultenc = copyToIndex(resultenc,bErrorMsg,index);
            // Add 0 as the last byte:
            resultenc[plength - 1] = 0;
            return resultenc;  
        }
        else if(message.getPacketType()== PacketType.DIRQ || message.getPacketType()== PacketType.DISC){
            resultenc = new byte[2];
            // Encode Opcode:
            resultenc = copyToIndex(resultenc, message.getOpcode(), index);
            return resultenc;
        }
        else if(message.getPacketType()== PacketType.BCAST){
            byte[] bBCast = (message.getMessage()).getBytes();
            plength = 3 + bBCast.length + 1;
            resultenc = new byte[plength];
            // Encode opcode
            resultenc = copyToIndex(resultenc,message.getOpcode(),index);
            index += message.getOpcode().length;
            // Encode deelted(0) or added(1)
            if(message.getAddedOrDeleted() == AddedOrDeleted.ADDED){
                resultenc[index]=(byte)1;
            }else{
                resultenc[index]=(byte)0;
            }
            index++;
            // Encode bcast file name:
            resultenc = copyToIndex(resultenc, bBCast, index);
            // Add 0 as the last byte:
            resultenc[plength - 1] = 0;
            return resultenc;
        }
        return null;
    }

    // Help Function: copying bytes[] toAdd to bytes[] result to a given index in bytes[] result
    public byte[] copyToIndex(byte[] result, byte[] toAdd, int index){
        for (byte b : toAdd) {
            result[index] = b;
            index++;
        }
        return result;
    }

    // Help Function: Initialize all fields when we finished decoding
    public DecodedMessage init_encoder_decoder(DecodedMessage result){
        this.result = new DecodedMessage();
        this.temp_bytes = new byte[2];
        this.message_bytes = new byte[1 << 9];
        this.tempLen = 0;
        this.len = 0;
        return result;
    }
}