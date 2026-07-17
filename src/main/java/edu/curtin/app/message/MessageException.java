package edu.curtin.app.message;

import edu.curtin.app.SimulationException;

public class MessageException extends SimulationException {
    public MessageException(String message) { 
        super(message); 
    }
    
    public MessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
