package edu.curtin.app.ship;

import edu.curtin.app.SimulationException;

public class ShipException extends SimulationException {
    public ShipException(String message) { 
        super(message); 
    }
    
    public ShipException(String message, Throwable cause) {
        super(message, cause);
    }
}
