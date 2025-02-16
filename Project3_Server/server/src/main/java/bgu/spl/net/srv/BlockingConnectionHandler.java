package bgu.spl.net.srv;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.impl.tftp.ConnectionsImpl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;



public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;
    private ConnectionsImpl<T> connectionsServer;
    private int connectionId;
    private BlockingQueue<T> tasksQueue;
    private Semaphore lock;
    private String name;


    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, BidiMessagingProtocol<T> protocol, ConnectionsImpl<T> connectionsServer, int connectionId) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.connectionsServer = connectionsServer;
        this.connectionId = connectionId;
        this.tasksQueue = new LinkedBlockingQueue<>();
        this.lock = new Semaphore(1 , true);
        this.name = null;
    }

    @Override
    public void run() {
        protocol.start(connectionId, connectionsServer);
        try (Socket sock = this.sock) { //just for automatic closing
            int read;
            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());
            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0 ) {
                T nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    protocol.process(nextMessage);
                } 
                while (tasksQueue.peek() != null) {
                    T msg = tasksQueue.poll();
                    this.send(msg);
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }finally {
            try {
                this.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        connectionsServer.disconnect(connectionId);
        sock.close();
    }

    @Override
    public void send(T msg) { 
        try{
            if (msg != null) {
                // If there is a case in which two threads are trying to send in the same time, one thread will be blocked
                lock.acquire();
                out.write(encdec.encode(msg));
                out.flush();
                lock.release();
            }
        }catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
        
    }
    public void submit(T msg) { 
        if (this.lock.tryAcquire()) {
            if (msg != null){
                try {
                    out.write(encdec.encode(msg));
                    out.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                lock.release();
            }
            
        }else{
            tasksQueue.offer(msg);
        }    
    }

    public String getName(){
        return this.name;
    }

    public void setName(String name){
        this.name = name;
    }
}
