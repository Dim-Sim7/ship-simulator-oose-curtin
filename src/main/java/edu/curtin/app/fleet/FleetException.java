package edu.curtin.app.fleet;

import edu.curtin.app.SimulationException;

public class FleetException extends SimulationException {
    public FleetException(String message) { 
        super(message); 
    }
    
    public FleetException(String message, Throwable cause) {
        super(message, cause);
    }
}
