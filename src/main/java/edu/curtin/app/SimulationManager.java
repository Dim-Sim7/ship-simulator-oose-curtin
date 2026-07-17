package edu.curtin.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.fleet.Fleet;
import edu.curtin.app.fleet.FleetCoordinator;
import edu.curtin.app.message.MessageException;
import edu.curtin.app.message.ParseStrategy;
import edu.curtin.app.message.PortMessageParser;
import edu.curtin.app.message.Scenario;
import edu.curtin.app.message.ShipMessageParser;
import edu.curtin.app.message.StormMessageParser;
import edu.curtin.app.message.SupplyMessageParser;
import edu.curtin.app.ship.ShipFactory;
import edu.curtin.app.ship.ShipSpawner;
import edu.curtin.app.storm.Storm;
import edu.curtin.app.storm.StormFactory;
import edu.curtin.app.storm.StormManager;
import edu.curtin.app.task.TaskFactory;
import edu.curtin.app.view.Journal;
import edu.curtin.app.view.LakeDisplay;

/**
 * Central coordinator for the simulation
 *
 * Responsible for:
 * - wiring all subsystems together
 * - processing scenario messages
 * - updating the simulation world
 * - calling display to render output
 */
public class SimulationManager {

    private static final Logger logger = Logger.getLogger(SimulationManager.class.getName());

    // Core simulation state
    private final Scenario scenario;
    private final Fleet fleet;

    // Controllers / managers
    private final FleetCoordinator coordinator;
    private final StormManager stormManager;

    // View / output systems
    private final LakeDisplay display;
    private final Journal journal;

    // Message parsing strategies
    private final List<ParseStrategy> strategies;

    public SimulationManager() {
        
        // Core simulation models
        this.fleet = new Fleet();
        this.scenario = new Scenario();
        this.journal = new Journal();
        
        // Object factories -- only used as injected dependencies
        TaskFactory taskFactory = new TaskFactory();
        ShipFactory shipFactory = new ShipFactory();
        StormFactory stormFactory = new StormFactory();
        ShipSpawner shipSpawner = new ShipSpawner(shipFactory, fleet);

        // Simulation controllers
        this.coordinator = new FleetCoordinator(fleet, taskFactory, journal, shipSpawner);
        this.stormManager = new StormManager(fleet, journal);

        // Visual display for the lake simulation
        this.display = new LakeDisplay(scenario.getHeight(), scenario.getWidth());

        // Message parsing system using strategy pattern
        this.strategies = List.of(
            // Handles incoming ship creation messages
            new ShipMessageParser(shipSpawner, coordinator),
            // Handles port creation messages
            new PortMessageParser(fleet),
            // Handles storm creation messages
            new StormMessageParser(stormFactory, stormManager),
            // Handles cargo supply requests
            new SupplyMessageParser(coordinator)
        );

    }

    /**
     * Main simulation loop
     * Runs continuously until user input is detected
     */
    public void run() {
        logger.info("Simulation started");
        try {
            while (System.in.available() == 0) {
                processScenarioMessages();
                updateWorld();
                sleep();
            }
            logger.info("Simulation ended - input detected");
        } catch (IOException e) {
            throw new SimulationException("IO error in simulation loop", e);
        } finally {
            // Ensure resources are closed cleanly
            journal.close();
        }
    }

    /**
     * Reads and processes all pending scenario messages
     * Each parser strategy determines whether it can
     * handle the message format.
     */
    private void processScenarioMessages() {
        String rawMessage;
        String[] message;

        while ((rawMessage = scenario.nextMessage()) != null) {
            journal.logScenarioMessage(rawMessage);
            message = rawMessage.split("\\s+");
            for (ParseStrategy strategy : strategies) {
                if (strategy.canParse(message)) {
                    try {
                        strategy.parse(message);
                    } catch (MessageException e) {
                        logger.log(Level.INFO, () -> "Skipping unparseable message: " + e.getMessage());
                    }
                    break;
                }
            }
        }
    }

    /**
     * Updates all the major subsystem for the current tick
     * Each subsystem here implements updatable to signal they need to be updated
     */
    private void updateWorld() {
        // Write new hour to file
        journal.update();

        // Update ships based on their current state and task
        fleet.update(); 

        // Retry deferred rescues and task coordination
        coordinator.update();
        
        // Copy storms before update because storms
        // dissipate during stormManager.update()
        List<Storm> storms = new ArrayList<>(stormManager.getStorms());
        // Apply storm damage and dissipate storms
        stormManager.update();

        // Render current simulation state
        display.render(fleet.getShips(), fleet.getPorts(), storms);
    }

    /**
     * Sets the simulation delay interval
     */
    private void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
