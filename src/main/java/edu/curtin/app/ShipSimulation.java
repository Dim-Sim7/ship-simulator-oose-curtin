package edu.curtin.app;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class for the Ship Simulation App
 * Calls Simulation Manager run()
 */
public class ShipSimulation {
    
    private static final Logger logger = Logger.getLogger(ShipSimulation.class.getName());
    public static void main(String[] args) {
        SimulationManager simManager = new SimulationManager();
        try {
            simManager.run();
        } catch (SimulationException e) {
            logger.log(Level.SEVERE, "Simulation encountered runtime error", e);
        }
    }

}
