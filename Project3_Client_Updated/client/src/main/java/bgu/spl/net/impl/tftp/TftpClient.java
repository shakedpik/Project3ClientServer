package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.Socket;



public class TftpClient {
    public static void main(String[] args) {
        if (args.length == 0){
            args = new String[]{"localhost", "7777"};
        }
        else if (args.length < 2) {
            System.out.println("you must supply two arguments: host, message");
            System.exit(1);
        }
        try (Socket sock = new Socket(args[0], Integer.valueOf(args[1]));
                BufferedInputStream socketIn = new BufferedInputStream(sock.getInputStream());//input from the recieving socket
                BufferedReader keyBoardIn = new BufferedReader(new InputStreamReader(System.in));//input from the keyborad
                BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream())) {
                    KeyboardThread keyboard = new KeyboardThread(keyBoardIn, out);
                    Thread keyboardThread = new Thread(keyboard);
                    Thread listeningThread = new Thread(new ListeningThread(socketIn, out, keyboard));
                    keyboardThread.start();
                    listeningThread.start(); 
                    keyboardThread.join();
                    listeningThread.join();
                    
        } catch(IOException | InterruptedException e){
            System.out.println("Connection to server failed, check if the server is currently working.");
        }

    }
}
