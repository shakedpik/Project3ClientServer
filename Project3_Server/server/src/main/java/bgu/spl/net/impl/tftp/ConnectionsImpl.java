package bgu.spl.net.impl.tftp;


import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;


public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> ids_to_handlers;



    public ConnectionsImpl() {
        this.ids_to_handlers = new ConcurrentHashMap<>();
    }

    public void connect(int connectionId, BlockingConnectionHandler<T> handler){
        // Add client connectionId, with his connection handler to ids_to_handlers map
        ids_to_handlers.put(connectionId, handler);
    }

    public boolean send(int connectionId, T msg){
        // Send a message to client with connectionId 
        BlockingConnectionHandler<T> handler = ids_to_handlers.get(connectionId);
        if (handler != null){
            handler.submit(msg);
            return true;
        }
        return false;
    }

    public void disconnect(int connectionId){
        // Removes an active client connectionId from ids_to_handlers map
        BlockingConnectionHandler<T> handler = ids_to_handlers.get(connectionId);
        if (handler != null){
            ids_to_handlers.remove(connectionId);
        }
    }
    
    public ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> getIdHashMap(){
        return this.ids_to_handlers;
    } 

}
