package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.impl.tftp.DecodedMessage.PacketType;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<DecodedMessage>  {

    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<DecodedMessage> connections;
    private boolean loggedIn;
    private List<byte[]> listDataPackets;
    private String fileName;
    private String packetTypeCase;




    @Override
    //Initiate the protocol with the active connections structure of the server
    // and saves the owner client’s connection id.
   // the method is given with the parameter : Connections<byte[]> connections
    public void start(int connectionId, Connections<DecodedMessage> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.loggedIn = false;
        this.listDataPackets = new ArrayList<>();
        this.fileName = "";
        this.packetTypeCase = "";
    }

    @Override
    public void process(DecodedMessage message) {
        PacketType packetType = message.getPacketType();
        DecodedMessage response = new DecodedMessage();
        if (packetType == null){
            response.setPacketType(PacketType.ERROR);
            response.setError((short)4);
            response.setMessage("Illegal Tftp Operation- unknown opcode.");
            connections.send(connectionId, response);
            System.out.println("ERROR 4");
        }
        else if (!loggedIn){
            if(packetType == PacketType.LOGRQ){
                if (nameIsTaken(message.getMessage())){
                    response.setPacketType(PacketType.ERROR);
                    response.setError((short)7);
                    response.setMessage("User with this name is already logged in.");
                    connections.send(connectionId, response);
                    System.out.println("ERROR 7");
                }else{
                    this.connections.getIdHashMap().get(connectionId).setName(message.getMessage());
                    response.setPacketType(PacketType.ACK);
                    response.setBlockNumber((short)0);
                    connections.send(connectionId, response);
                    loggedIn = true;            
                    System.out.println("ACK 0");
                }

            }else{
                response.setPacketType(PacketType.ERROR);
                response.setError((short)6);
                response.setMessage("User not logged in!");
                connections.send(connectionId, response);
                System.out.println("ERROR 6");
            }
        }
        else if(packetType == PacketType.LOGRQ){
            response.setPacketType(PacketType.ERROR);
            response.setError((short)7);
            response.setMessage("User already logged in!");
            connections.send(connectionId, response);
            System.out.println("ERROR 7");
        }
        else if(packetType == PacketType.RRQ){
            this.fileName = message.getMessage();
            this.packetTypeCase = "RRQ";
            String filePath = "./Files/" + fileName;
            File file = new File(filePath);
            if (!file.exists()) {
                response.setPacketType(PacketType.ERROR);
                response.setError((short)1);
                response.setMessage("File not found - RRQ of non-existing file.");
                connections.send(connectionId, response);
                System.out.println("ERROR 1");
            }else{
                try {
                    byte[] answer = readFileToBytes(filePath);
                    splitByteArray(answer, 512);
                } catch (IOException e) {
                    response.setPacketType(PacketType.ERROR);
                    response.setError((short)2);
                    response.setMessage("Access violation- file cannot be read.");
                    connections.send(connectionId, response);
                    System.out.println("ERROR 2");
                }
                response.setPacketType(PacketType.DATA);
                response.setPacketSize((short)listDataPackets.get(0).length);
                response.setBlockNumber((short)1);
                response.setDataBuffer(listDataPackets.get(0));
                connections.send(connectionId, response);
                System.out.println("DATA 1"); //Maybe remove later
            }     
        }
        else if(packetType == PacketType.WRQ){
            this.fileName = message.getMessage();
            String filePath = "./Files/" + this.fileName;
            File file = new File(filePath);
            if(file.exists()){
                response.setPacketType(PacketType.ERROR);
                response.setError((short)5);
                response.setMessage("File already exists, "+ fileName + " exists on WRQ.");
                connections.send(connectionId, response);
                this.fileName = "";
                System.out.println("ERROR 5");
            }else{
                this.packetTypeCase = "WRQ";
                response.setPacketType(PacketType.ACK);
                response.setBlockNumber((short)0);
                connections.send(connectionId, response);
                System.out.println("ACK 0");

            }
        }
        else if(packetType == PacketType.DATA){
            ByteBuffer byte_buffer = message.getDataBuffer();
            byte[] data_bytes = new byte[byte_buffer.remaining()] ;
            byte_buffer.get(data_bytes);

            this.listDataPackets.add(data_bytes);
            response.setPacketType(PacketType.ACK);
            response.setBlockNumber((short)(message.getBlockNumber()));
            connections.send(connectionId, response);
            System.out.println("ACK "+ (message.getBlockNumber() + 1));

            

            if(message.getPacketSize() < (short)512){
                byte[] uploadedFile = combineByteArrays(this.listDataPackets);
                String filePath = "./Files/" + this.fileName;

                try (FileOutputStream fos = new FileOutputStream(filePath)) {
                    fos.write(uploadedFile);
                    System.out.println(this.packetTypeCase + " " + this.fileName + " complete");
                    try{
                        DecodedMessage bcast_response = new DecodedMessage();
                        bcast_response.setPacketType(PacketType.BCAST);
                        bcast_response.setAddedOrDeleted(true);
                        bcast_response.setMessage(this.fileName);
                        for (Integer connectionId : connections.getIdHashMap().keySet()){ 
                            connections.send(connectionId, bcast_response);
                            System.out.println("BCAST " + connectionId);
                        }
                    }catch (Exception e){}
                    
                }catch (Exception e){
                    response.setPacketType(PacketType.ERROR);
                    response.setError((short)2);
                    response.setMessage("Access violation- file cannot be written.");
                    connections.send(connectionId, response);
                    System.out.println("ERROR 2");
                }
                this.fileName = "";
                this.packetTypeCase = "";
                this.listDataPackets.clear();
            }
 
        }
        else if (packetType == PacketType.ACK){
            short blockNumber = message.getBlockNumber();
            if(blockNumber >= listDataPackets.size()){
                if (this.packetTypeCase == "DIRQ"){
                    System.out.println(this.packetTypeCase + " complete");
                }else{
                    System.out.println(this.packetTypeCase + " " + this.fileName + " complete");
                }
                this.fileName = "";
                this.packetTypeCase = "";
                this.listDataPackets.clear();
            }else{
                response.setPacketType(PacketType.DATA);
                response.setPacketSize((short)listDataPackets.get(blockNumber).length);
                response.setBlockNumber((short)(blockNumber+1));
                response.setDataBuffer(listDataPackets.get(blockNumber));
                connections.send(connectionId, response);
                System.out.println("DATA " + (blockNumber+1));
            }
        }
        else if (packetType == PacketType.DIRQ){  
            this.packetTypeCase = "DIRQ";  
            File directory = new File("./Files");
            // Check if the specified path points to a directory
            if (directory.isDirectory()) {
                // List all files in the directory
                File[] files = directory.listFiles();
                if (files != null && files.length > 0) {
                    ArrayList<byte[]> files_bytes = new ArrayList<>();
                    int counter = 0;
                    for (File file : files) {
                        if (file.isFile()) {
                            String file_name = file.getName();
                            files_bytes.add(file_name.getBytes());
                            counter += file_name.getBytes().length;
                        }
                    }
                    counter += files_bytes.size(); //space for zeros
                    byte[] answer = new byte[counter];
                    int index = 0;
                    for (byte[] file : files_bytes){
                        answer = copyToIndex(answer, file, index);
                        index += file.length;
                        answer[index] = (byte)0;
                        index ++;
                    }
                    answer = Arrays.copyOf(answer, answer.length - 1);
                    splitByteArray(answer, 512);
  
                    response.setPacketType(PacketType.DATA);
                    response.setPacketSize((short)listDataPackets.get(0).length);
                    response.setBlockNumber((short)1);
                    response.setDataBuffer(listDataPackets.get(0));
                    connections.send(connectionId, response);
                    System.out.println("DATA 1");

                } 
                else if(files != null){
                    response.setPacketType(PacketType.ERROR);
                    response.setError((short)0);
                    response.setMessage("Nothing in this direcory");
                    connections.send(connectionId, response);
                    System.out.println("ERROR 0");
                }
            }
            else{
                response.setPacketType(PacketType.ERROR);
                response.setError((short)1);
                response.setMessage("Directory not found");
                connections.send(connectionId, response);
                System.out.println("ERROR 1");

            }

        } 
            
        else if(packetType == PacketType.DELRQ){
            String filePath = "./Files/" + message.getMessage();
            File file = new File(filePath);
            if (!file.exists()) {
                response.setPacketType(PacketType.ERROR);
                response.setError((short)1);
                response.setMessage("File not found - DELRQ of non-existing file.");
                connections.send(connectionId, response);
                System.out.println("ERROR 1");
            }else{
                try{
                    file.delete();
                    response.setPacketType(PacketType.ACK);
                    response.setBlockNumber((short)0);
                    connections.send(connectionId, response);
                    System.out.println("ACK 0");
                    DecodedMessage bcast_response = new DecodedMessage();
                    bcast_response.setPacketType(PacketType.BCAST);
                    bcast_response.setAddedOrDeleted(false);
                    bcast_response.setMessage(message.getMessage());
                    for (Integer connectionId : connections.getIdHashMap().keySet()){ 
                        connections.send(connectionId, bcast_response);
                        System.out.println("BCAST " + connectionId);
                    }

                }catch(Exception e){
                    response.setPacketType(PacketType.ERROR);
                    response.setError((short)2);
                    response.setMessage("Access violation- file cannot be deleted.");
                    connections.send(connectionId, response);
                    System.out.println("ERROR 2");
                }
            }
        }

        else if (packetType == PacketType.DISC){
            response.setPacketType(PacketType.ACK);
            response.setBlockNumber((short)0);
            connections.send(connectionId, response);
            System.out.println("ACK 0");
            shouldTerminate =true;
        }

    }
    private void splitByteArray(byte[] originalArray, int chunkSize){
        int index = 0;
        while (index < originalArray.length) {
            int length = Math.min(chunkSize, originalArray.length - index);
            byte[] chunk = new byte[length];
            System.arraycopy(originalArray, index, chunk, 0, length);
            this.listDataPackets.add(chunk);
            index += length;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

    public static byte[] readFileToBytes(String filePath) throws IOException {
        // Open the file input stream
        try (FileInputStream fis = new FileInputStream(filePath)) {
            // Get the size of the file
            long fileSize = fis.available();
            
            // Create a byte array to hold the file data
            byte[] buffer = new byte[(int) fileSize];
            
            // Read the file into the buffer
            fis.read(buffer);
            
            return buffer;
        }catch (IOException e) {}
        return null;
    }

    // Method to combine byte arrays
    public static byte[] combineByteArrays(List<byte[]> arrays) {
        // Calculate the total length of the combined array
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }

        // Create the combined array
        byte[] combinedArray = new byte[totalLength];

        // Copy the contents of each array into the combined array
        int currentIndex = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, combinedArray, currentIndex, array.length);
            currentIndex += array.length;
        }

        return combinedArray;
    }

    public byte[] copyToIndex(byte[] result, byte[] toAdd, int index){
        for (byte b : toAdd) {
            result[index] = b;
            index++;
        }
        return result;
    }

    public boolean nameIsTaken(String name){
        for (BlockingConnectionHandler<DecodedMessage> handler : this.connections.getIdHashMap().values()){
            if (handler.getName() != null && handler.getName().equals(name)){
                return true;
            }
        }
        return false;
    }
}
