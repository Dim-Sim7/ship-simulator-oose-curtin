package edu.curtin.app.storm;

import edu.curtin.app.SimulationException;

public class StormException extends SimulationException {
    public StormException(String message) { 
        super(message); 
    }
    
    public StormException(String message, Throwable cause) {
        super(message, cause);
    }
}
