package bgu.spl.net.impl.tftp;
import bgu.spl.net.srv.Server;

public class TftpServer{
    /**
     * The main loop of the server, Starts listening and handling new clients.
     */
     public static void main(String[] args) { 
        if (args.length == 0){
            args = new String[]{"7777"};
        }
        Server.threadPerClient(
                Integer.valueOf(args[0]), //port
                ()-> new TftpProtocol(), //protocol factory
                ()-> new TftpEncoderDecoder() //message encoder decoder factory
        ).serve();
    }

}
