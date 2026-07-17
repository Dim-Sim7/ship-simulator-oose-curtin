package edu.curtin.app.task;

import edu.curtin.app.SimulationException;

public class TaskException extends SimulationException {
    public TaskException(String message) { 
        super(message); 
    }
    
    public TaskException(String message, Throwable cause) {
        super(message, cause);
    }

}
