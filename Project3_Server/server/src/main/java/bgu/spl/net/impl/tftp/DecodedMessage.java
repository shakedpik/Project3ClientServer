package bgu.spl.net.impl.tftp;

import java.nio.ByteBuffer;

public class DecodedMessage {
    private byte[] opcode;
    private String message;
    enum PacketType{
        undefined, 
        RRQ,
        WRQ,
        DATA,
        ACK,
        ERROR,
        DIRQ,
        LOGRQ,
        DELRQ,
        BCAST,
        DISC
    }
    private PacketType packetType;
    private Short packetSize;
    private Short blockNumber;
    private Short errorCode;
    enum AddedOrDeleted{
        undefined, 
        ADDED,
        DELETED,
    }
    private AddedOrDeleted addedOrDeleted;
    private ByteBuffer dataBuffer;
    

    public DecodedMessage() {
        this.opcode = null;
        this.message = null;
        this.packetType = PacketType.undefined;
        this.packetSize = null;
        this.blockNumber = null;
        this.errorCode = null;
        this.addedOrDeleted = AddedOrDeleted.undefined;
        this.dataBuffer = ByteBuffer.allocate(512);
    }

    public DecodedMessage(byte[] opcode, String message) {
        setOpcode(opcode);
        this.message = message; 
    }
    public void setDataBuffer(byte[] bytes){
        this.dataBuffer = ByteBuffer.wrap(bytes);
    }
    public ByteBuffer getDataBuffer(){
        return this.dataBuffer;
    }

    public byte[] getOpcode() {
        return opcode;
    }
    public void setOpcode(byte[] opcode) {
        this.opcode = opcode;
        if (opcode[0]== (byte)0){
            if (opcode[1] == (byte)1 ){
                this.packetType = PacketType.RRQ;
            }else if (opcode[1] == (byte)2 ){
                this.packetType = PacketType.WRQ;
            }else if (opcode[1] == (byte)3 ){
                this.packetType = PacketType.DATA;
            }else if (opcode[1] == (byte)4 ){
                this.packetType = PacketType.ACK;
            }else if (opcode[1] == (byte)5 ){
                this.packetType = PacketType.ERROR;
            }else if (opcode[1] == (byte)6 ){
                this.packetType = PacketType.DIRQ;
            }else if (opcode[1] == (byte)7 ){
                this.packetType = PacketType.LOGRQ;
            }else if (opcode[1] == (byte)8 ){
                this.packetType = PacketType.DELRQ;
            }else if (opcode[1] == (byte)9 ){
                this.packetType = PacketType.BCAST;
            }else if (opcode[1] == (byte)10 ){
                this.packetType = PacketType.DISC;
            }
        }
        
    }

    public void setPacketType(PacketType packetType) {
        byte[] opcode = new byte[2];
        opcode[0] = (byte)0;
        this.packetType = packetType;
        if (packetType == PacketType.RRQ ){
            opcode[1] = (byte)1;
        }else if (packetType == PacketType.WRQ ){
            opcode[1] = (byte)2;
        }else if (packetType == PacketType.DATA ){
            opcode[1] = (byte)3;
        }else if (packetType == PacketType.ACK ){
            opcode[1] = (byte)4;
        }else if (packetType == PacketType.ERROR ){
            opcode[1] = (byte)5;
        }else if (packetType == PacketType.DIRQ ){
            opcode[1] = (byte)6;
        }else if (packetType == PacketType.LOGRQ ){
            opcode[1] = (byte)7;
        }else if (packetType == PacketType.DELRQ ){
            opcode[1] = (byte)8;
        }else if (packetType == PacketType.BCAST ){
            opcode[1] = (byte)9;
        }else if (packetType == PacketType.DISC ){
            opcode[1] = (byte)10;
        }
        this.opcode = opcode;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public void setPacketSize(Short packetSize) {
        this.packetSize = packetSize;
    }
    public void setBlockNumber(Short blockNumber) {
        this.blockNumber = blockNumber;
    }
    public Short getPacketSize() {
        return this.packetSize;
    }
    public Short getBlockNumber() {
        return this.blockNumber;
    }
    public String getMessage() {
        return this.message;
    }
    public PacketType getPacketType() {
        return this.packetType;
    }
    public Short getError() {
        return this.errorCode;
    }
    public void setError(Short error) {
        this.errorCode = error;
    }
    public AddedOrDeleted getAddedOrDeleted() {
        return this.addedOrDeleted;
    }
    public void setAddedOrDeleted(boolean added) {
        if(added){
            this.addedOrDeleted = AddedOrDeleted.ADDED;
        }else{
            this.addedOrDeleted = AddedOrDeleted.DELETED;
        }
    }

}