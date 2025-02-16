package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import bgu.spl.net.impl.tftp.DecodedMessage.PacketType;

public class KeyboardThread implements Runnable {
    private volatile boolean shouldTerminate;
    private BufferedReader keyBoardIn;
    private BufferedOutputStream out;
    private DecodedMessage clientRequest;
    private TftpEncoderDecoder encdec;
    public Object keyboardLocker; // The keyboard thread sleeps on this object

    public KeyboardThread(BufferedReader in, BufferedOutputStream out){
        this.keyBoardIn = in;
        this.out = out;
        this.shouldTerminate = false;
        this.clientRequest = new DecodedMessage();
        this.encdec = new TftpEncoderDecoder();
        this.keyboardLocker = new Object();
    }
    public boolean shouldTerminate(){
        return this.shouldTerminate;
    }
    public void terminate(){
        this.shouldTerminate = true;
    }

    public DecodedMessage getClientRequest(){
        return this.clientRequest;
    }
    @Override
    public void run() {
        while (!shouldTerminate) {
            try {
                String line;
                if ((line = keyBoardIn.readLine()) != null){ // Reading the next line from the keyboard
                    this.clientRequest = new DecodedMessage(); // Setting a new packet (in order to send the server)
                    int spaceIndex = line.indexOf(' ');
                    String rest;
                    String firstWord;
                    if (spaceIndex != -1) {
                        firstWord = line.substring(0, spaceIndex); // The first word will define the packet type
                        // Extract the substring starting from the character after the first space
                        rest = line.substring(spaceIndex + 1);
                    }else{
                        firstWord = line;
                        rest = null;
                    }
                    settingDecodedMessage(firstWord, rest); // Setting the Decoded message
                    // Add synchronization
                    if (clientRequest.getPacketType() != PacketType.undefined){
                        synchronized (keyboardLocker){
                            out.write(this.encdec.encode(this.clientRequest));
                            out.flush(); 
                            try {
                                keyboardLocker.wait();
                            } catch (InterruptedException e) {
                            }
                        }
                    }  
                }                    
            }catch (IOException e) {
                e.printStackTrace(); // IO Exception
            }
        }
    }

    private void settingDecodedMessage(String type, String rest){
        if ((rest == null || rest.equals("")) && !type.equals("DIRQ") && !type.equals("DISC")){
            // In case that there is no second word
            System.out.println("Invalid command.");
        }
        else if(type.equals("RRQ")){ 
            String filePath = "./Files/" + rest;
            File file = new File(filePath);
            if (file.exists()) {
                System.out.println("File " + rest +" already exists in the client.");
            }else{
                this.clientRequest.setPacketType(PacketType.RRQ);
                this.clientRequest.setMessage(rest);
            }  
        }
        else if (type.equals("WRQ")){
            String filePath = "./Files/" + rest;
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File " + rest +" wasn't found in the client.");
            }else{
                this.clientRequest.setPacketType(PacketType.WRQ);
                this.clientRequest.setMessage(rest);
            }  
        }
        else if (type.equals("LOGRQ")){
            this.clientRequest.setPacketType(PacketType.LOGRQ);
            this.clientRequest.setMessage(rest);
        }
        else if (type.equals("DELRQ")){
            this.clientRequest.setPacketType(PacketType.DELRQ);
            this.clientRequest.setMessage(rest);
        }
        else if (type.equals("DIRQ")){
            this.clientRequest.setPacketType(PacketType.DIRQ);
        }
        else if (type.equals("DISC")){
            this.clientRequest.setPacketType(PacketType.DISC);
        }else{
            System.out.println("Invalid command.");
        }     
    }
    
}