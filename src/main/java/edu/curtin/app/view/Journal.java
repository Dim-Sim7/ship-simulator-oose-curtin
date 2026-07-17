package edu.curtin.app.view;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.SimulationException;
import edu.curtin.app.utility.Updatable;

/**
 * Writes to journal.txt as per the assignment specification
 * Associated in controllers such as FleetCoordinator and StormManager to get
 * context specific information for logging
 */
public class Journal implements Updatable {
    private static final Logger logger = Logger.getLogger(Journal.class.getName());
    
    private static final String OUTPUTFILE = "journal.txt";
    private int currentHour;
    private PrintWriter writer;

    public Journal() {
        currentHour = 0;
        try {
            writer = new PrintWriter(new FileWriter(OUTPUTFILE, false), true);
            writer.println("=== Simulation Start ===");
            logger.log(Level.INFO, () -> "Journal opened: " + OUTPUTFILE);
        } catch (IOException e) {
            throw new SimulationException("Failed to open journal file", e);
        }
    }

    public void close() {
        logger.log(Level.INFO, () -> "Journal closed at hour: " + currentHour);
        writeLine("=== Simulation End ===");
        writer.close();
    }

    @Override
    public void update() {
        currentHour++;
        writeLine("\n--- Hour " + currentHour + " ---");
    }

    private void writeLine(String msg) {
        writer.println(msg);
        writer.flush();
    }

    public void logScenarioMessage(String message) {
        writeLine("SCENARIO message: " + message);
    }

    public void logSupplyRunAccepted(int shipId, String taskDescription) {
        writeLine("Supply run ACCEPTED for ship " + shipId + " " + taskDescription);
    }

    public void logIgnoreSupplyRun(String taskDescription) {
        writeLine("Supply run IGNORED: " + taskDescription);
    }

    public void logSunk(int shipId) {
        writeLine("SHIP SUNK: id = " + shipId);
    }

    public void logDisabled(String shipDescription) {
        writeLine("DISABLED ship " + shipDescription);
    }

    public void logRescueAssigned(int rescueShipId, int disabledShipId) {
        writeLine("RESCUE ASSIGNED: diverting ship " + rescueShipId + " to rescue ship " + disabledShipId);
    }

    public void logRescueAbandoned(int rescuingShipId, int disabledShipId) {
        writeLine("RESCUE ABANDONED by ship " + rescuingShipId + " for disabled ship " + disabledShipId);
    }

    public void logNeedPortRepair(int shipId) {
        writeLine("REPAIR at port tasked for ship " + shipId);
    }

    public void logTaskCompleted(int shipId, String completedTaskDescription) {
        writeLine("TASK completed by ship " + shipId + ": " + completedTaskDescription);
    }

    public void logShipEnteredPort(int shipId, String portId, String activity) {
        writeLine("ENTER PORT: ship " + shipId
            + " at port " + portId
            + " [" + activity + "]");
    }

    public void logShipExitPort(int shipId, String portId) {
        writeLine("EXIT PORT: ship " + shipId
                + " from port " + portId);
    }

    public void logStormHitShip(int stormRadius, int shipId, String outcome) {
        writeLine("STORM HIT: ship " + shipId
                + " at storm radius " + stormRadius +  " - " + outcome);
    }
}