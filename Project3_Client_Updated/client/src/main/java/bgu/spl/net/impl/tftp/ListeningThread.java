package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bgu.spl.net.impl.tftp.DecodedMessage.AddedOrDeleted;
import bgu.spl.net.impl.tftp.DecodedMessage.PacketType;

public class ListeningThread implements Runnable {
    private BufferedInputStream sockIn;
    private BufferedOutputStream out;
    private KeyboardThread keyboard; 
    private TftpEncoderDecoder encdec;
    private List<byte[]> listDataPackets;

    public ListeningThread(BufferedInputStream in,  BufferedOutputStream out, KeyboardThread keyboard){
        this.sockIn = in;
        this.out = out;
        this.keyboard = keyboard;
        this.encdec = new TftpEncoderDecoder();
        this.listDataPackets = new ArrayList<>();
    }

    @Override
    public void run() {
        int read;
        try {
            while (!keyboard.shouldTerminate() && (read = this.sockIn.read()) >= 0) {
                DecodedMessage nextMessage = encdec.decodeNextByte((byte) read); 
                if (nextMessage != null) {
                    process(nextMessage);     
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    
    // Processing the packet that was recieved from the server
    public void process(DecodedMessage message){
        DecodedMessage responseToServer = new DecodedMessage(); // Response Packet (if it's needed)
        DecodedMessage clientRequest = keyboard.getClientRequest(); // The client's original request to the server
        if (message.getPacketType() == PacketType.DATA){
            // Reading the data from the Server's data packet:
            ByteBuffer byte_buffer = message.getDataBuffer();
            byte[] data_bytes = new byte[byte_buffer.remaining()] ;
            byte_buffer.get(data_bytes);

            // Adding the data to the list
            this.listDataPackets.add(data_bytes);

            // Setting ACK response packet:
            responseToServer.setPacketType(PacketType.ACK);
            responseToServer.setBlockNumber((short)(message.getBlockNumber()));
            
            System.out.println("ACK "+ (message.getBlockNumber() + 1));

            // Sending the ACK response to the server:
            try {
                this.out.write(encdec.encode(responseToServer));
                this.out.flush();
            } catch (IOException e) {}

            // There are two cases- the original request was RRQ or DIRQ:
            if (clientRequest.getPacketType() == PacketType.RRQ){
                // If this data packet was the last one - combine all the byte arrays that were recieved and write it to a new file:
                if (message.getPacketSize() < (short)512){
                    byte[] uploadedFile = combineByteArrays(this.listDataPackets);
                    String filePath = "./Files/" + clientRequest.getMessage();

                    // Writing the byte[] into new file:
                    try (FileOutputStream fos = new FileOutputStream(filePath)) {
                        fos.write(uploadedFile);
                        System.out.println("RRQ "+ clientRequest.getMessage() + " complete.");
                        
                        // Clearing the list:
                        this.listDataPackets.clear();

                        // Notifying the Keyboard Thread:
                        synchronized (this.keyboard.keyboardLocker){
                            this.keyboard.keyboardLocker.notifyAll();
                        }
                    }catch (Exception e){
                        System.out.println("Error - IO Exception in client side.");
                    }
                }
            }else if (clientRequest.getPacketType() == PacketType.DIRQ) {
                // If this data packet was the last one - print all the files' names
                if (message.getPacketSize() < (short)512){
                    byte[] dirqAnswer = combineByteArrays(this.listDataPackets);
                    String answer = "";
                    int startIndex = 0;
                    int endIndex = 0;
                    int len = 0;
                    for( byte b : dirqAnswer){
                        len++;
                        if(len == dirqAnswer.length-1){
                            // We are in the end of the array- there isn't 0 byte
                            byte[] cutArray = Arrays.copyOfRange(dirqAnswer, startIndex, endIndex+2); // Cutting the byte[] till the 0
                            String convertedFromByte = new String(cutArray, 0, cutArray.length, StandardCharsets.UTF_8);// Convertig all bytes till 0 to string
                            answer += "\n" +convertedFromByte;
                        }
                        if(b == (byte)0){
                            // It's like \n in the string
                            byte[] cutArray = Arrays.copyOfRange(dirqAnswer, startIndex, endIndex);// Cutting the byte[] till the 0
                            startIndex = endIndex;
                            endIndex = startIndex;
                            String convertedFromByte = new String(cutArray, 0, cutArray.length, StandardCharsets.UTF_8);// Convertig all bytes till 0 to string
                            answer += "\n" + convertedFromByte;
                        }
                        endIndex++;
                    }
                    System.out.println(answer);

                    // Clearing the list:
                    this.listDataPackets.clear();

                    // Notifying the Keyboard Thread:
                    synchronized (this.keyboard.keyboardLocker){
                        this.keyboard.keyboardLocker.notifyAll();
                    }
                }

            }
        }else if (message.getPacketType() == PacketType.ACK){
            // There are 4 cases: the original request was LOGRQ, WRQ, DELRQ or DISC
            if (clientRequest.getPacketType() == PacketType.LOGRQ){
                System.out.println("LOGRQ Completed- the user " + clientRequest.getMessage() +" is now connected to the server.");

                // Notifying the Keyboard Thread:
                synchronized (this.keyboard.keyboardLocker){
                    this.keyboard.keyboardLocker.notifyAll();
                }

            }else if (clientRequest.getPacketType() == PacketType.WRQ){
                if(this.listDataPackets.size() == 0){
                    String filePath = "./Files/" + clientRequest.getMessage();
                    try {
                        byte[] answer = readFileToBytes(filePath);
                        splitByteArray(answer, 512);
                    } catch (IOException e) {}
                }
                short blockNumber = message.getBlockNumber();
                
                // In case that the ack was for the last block in the list- WRQ is completed 
                if(blockNumber >= listDataPackets.size()){
                    System.out.println("WRQ " + clientRequest.getMessage() + " completed.");
                    // Clearing the list:
                    this.listDataPackets.clear();

                    // Notifying the Keyboard Thread:
                    synchronized (this.keyboard.keyboardLocker){
                        this.keyboard.keyboardLocker.notifyAll();
                    }
                }else{
                    // Setting the data packet with the correct information:
                    responseToServer.setPacketType(PacketType.DATA);
                    responseToServer.setPacketSize((short)listDataPackets.get(blockNumber).length);
                    responseToServer.setBlockNumber((short)(blockNumber+1));
                    responseToServer.setDataBuffer(listDataPackets.get(blockNumber));
                    
                    //Sending data packet to the server:
                    try {
                        this.out.write(encdec.encode(responseToServer));
                        this.out.flush();
                    } catch (IOException e) {}

                    System.out.println("DATA " + (blockNumber+1));
                }

            }else if (clientRequest.getPacketType() == PacketType.DELRQ){
                System.out.println("DELRQ Completed- the file " + clientRequest.getMessage() +" was deleted from the server.");
                
                // Notifying the Keyboard Thread:
                synchronized (this.keyboard.keyboardLocker){
                    this.keyboard.keyboardLocker.notifyAll();
                }
            }else if (clientRequest.getPacketType() == PacketType.DISC){
                System.out.println("DISC Completed.");

                // ACK for DISC means that the client can disconnect from the server
                this.keyboard.terminate();

                // Notifying the Keyboard Thread:
                synchronized (this.keyboard.keyboardLocker){
                    this.keyboard.keyboardLocker.notifyAll();
                }
            }
        }else if (message.getPacketType() == PacketType.ERROR){
            String error = "Error " + message.getError() + " , with error message: " + message.getMessage();
            System.out.println(error);

            // Notifying the Keyboard Thread:
            synchronized (this.keyboard.keyboardLocker){
                this.keyboard.keyboardLocker.notifyAll();
            } 
        }else if (message.getPacketType() == PacketType.BCAST){
            String bcast = "BCAST ";
            bcast += message.getAddedOrDeleted() == AddedOrDeleted.ADDED ? "Added " : "Deleted ";
            bcast += message.getMessage();
            System.out.println(bcast);

            // Notifying the Keyboard Thread:
            synchronized (this.keyboard.keyboardLocker){
                this.keyboard.keyboardLocker.notifyAll();
            }
        }
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
}