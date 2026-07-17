package edu.curtin.app.fleet;
import edu.curtin.app.ship.Ship;
import edu.curtin.app.ship.ShipObserver;
import edu.curtin.app.ship.ShipSpawner;
import edu.curtin.app.task.RescueTask;
import edu.curtin.app.task.Task;
import edu.curtin.app.task.TaskFactory;
import edu.curtin.app.task.TaskPriority;
import edu.curtin.app.utility.Updatable;
import edu.curtin.app.view.Journal;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.port.Port;

/**
 * Acts as a control system for ships
 * Observer of ships and responds to events created by ships
 * Assigns and creates tasks for ships
 */
public class FleetCoordinator implements ShipObserver, Updatable {
    private static final Logger logger = Logger.getLogger(FleetCoordinator.class.getName());
    
    private final Fleet fleet;
    private final TaskFactory taskFactory;
    private final Journal journal;
    private final ShipSpawner shipSpawner;

    // List of ships awaiting rescue until it is assigned a rescue ship
    private final List<Ship> awaitingRescue = new ArrayList<>();

    public FleetCoordinator(Fleet fleet, TaskFactory taskFactory, Journal journal, ShipSpawner shipSpawner) { 
        if (fleet == null || taskFactory == null || journal == null || shipSpawner == null) {
            throw new FleetException("FleetCoordinator dependencies cannot be null");
        }
        this.fleet = fleet;
        this.taskFactory = taskFactory;
        this.journal = journal;
        this.shipSpawner = shipSpawner;
    }

    public Fleet getFleet() {
        return fleet;
    }

    public void assignSupplyTask(Port pickupPort, Port dropoffPort, String cargo, int tonnes) { 
        if (pickupPort == null || dropoffPort == null) {
            throw new FleetException("Supply task requires valid pickup and dropoff ports");
        }
        // Find an idle ship 
        Ship ship = fleet.findIdleShip();
        // Create the supply task
        Task supplyTask = taskFactory.createSupplyTask(pickupPort, dropoffPort, cargo, tonnes);
        if (ship != null) {
            // Assign task
            ship.assignTask(supplyTask);
            journal.logSupplyRunAccepted(ship.getId(), supplyTask.toString());
            logger.log(Level.INFO, () -> "Supply task assigned to ship: id = " + ship.getId());
        } else {
            // Log ignored
            journal.logIgnoreSupplyRun(supplyTask.toString());
            logger.log(Level.INFO, () -> "No idle ship available, supply task ignored");
        }
    }

    /**
     * This updates simply retries rescues of ships in awaitingRescue
     */
    @Override
    public void update() {
        for (Ship ship : new ArrayList<>(awaitingRescue)) {
            if (ship.isDestroyed() || !ship.needsRescue() || assignRescue(ship)) {
                awaitingRescue.remove(ship);
            }
        }
    }

    /**
     * When a ship is sunk it is simply removed from the simulation
     * A new ship will be spawned in to take its place
     */
    @Override
    public void onShipSunk(Ship ship) {
        if (ship == null) {
            throw new FleetException("Cannot sink null ship");
        }
        logger.log(Level.INFO, () -> "Ship sunk: id = " + ship.getId());

        // If the sunk ship was on a rescue mission, re-queue the disabled ship
        if (ship.getCurrentTask() != null && ship.getCurrentTask().getTaskType() == TaskPriority.RESCUE) {
            RescueTask rescueTask = (RescueTask)ship.getCurrentTask();
            Ship targetShip =  rescueTask.getTargetShip();
            journal.logRescueAbandoned(ship.getId(), targetShip.getId());
            logger.log(Level.INFO, () -> "Rescuer sunk, re-queuing disabled ship: id=" + targetShip.getId());
            if (targetShip.needsRescue()) {
                awaitingRescue.add(targetShip);
            }
        }

        // Find a replacement port and spawn a new ship
        Port spawnPort = fleet.findNearestPort(ship);
        if (spawnPort == null) {
            throw new FleetException("No port available to spawn replacement for ship: id = " + ship.getId());
        }
        journal.logSunk(ship.getId());
        fleet.removeShip(ship.getId());
        shipSpawner.spawnReplacement(spawnPort.getX(), spawnPort.getY(), this);
        logger.log(Level.INFO, () -> "Replacement ship spawned at port: id = " + spawnPort.getId());
    }

    /**
     * Event when a ship becomes disabled
     * Attempts to find a rescue ship, if none are available, add to awaitingRescue list
     * Will be tried again in update()
     */
    @Override
    public void onShipDisabled(Ship ship) {
        if (ship == null) {
            throw new FleetException("Null ship cannot be disabled");
        }
        // If the disabled ship was on a rescue mission, clear the task and add the ship being rescued back to awaitingRescue
        if (ship.getCurrentTask() != null && ship.getCurrentTask().getTaskType() == TaskPriority.RESCUE) {

            RescueTask rescueTask = (RescueTask) ship.getCurrentTask();
            Ship targetShip = rescueTask.getTargetShip();
            journal.logRescueAbandoned(ship.getId(), targetShip.getId());

            // Re-queue target ship for rescue if it still needs it
            if (targetShip.needsRescue() && !awaitingRescue.contains(targetShip)) {
                awaitingRescue.add(targetShip);
            }
        }
        journal.logDisabled(ship.toString());
        if (!assignRescue(ship)) {
            awaitingRescue.add(ship);
            logger.log(Level.INFO, () -> "No rescue ship available, ship queued: id=" + ship.getId());
        }
    }

    /** 
     * Asks fleet to find a rescue ship 
     * A rescue ship is a ship that is not disabled and is not
     * already on a rescue mission
     * Tasks in progress are suspended for a rescue task
     */
    private boolean assignRescue(Ship disabledShip) {
        if (disabledShip == null) {
            throw new FleetException("Cannot assign a rescue ship to a null ship");
        }
        Ship rescueShip = fleet.findRescueShip();
        boolean hasAssignedRescue = false;
        if (rescueShip != null) {
            if (rescueShip.hasCurrentTask()) {
                rescueShip.suspendTask();
            }
            logger.log(Level.INFO, () -> "Rescuing ship: id=" + disabledShip.getId() + " with ship " + rescueShip.getId());
            journal.logRescueAssigned(rescueShip.getId(),  disabledShip.getId());
            rescueShip.assignTask(taskFactory.createRescueTask(disabledShip));
            hasAssignedRescue = true;
        }
        return hasAssignedRescue;
    }

    /**
     * Send ship to the nearest port for repairs
     * Event only sent while a ship is idle
     */
    @Override
    public void onNeedPortRepairs(Ship ship) {
        if (ship == null) {
            throw new FleetException("Cannot assign repair at port task to null ship");
        }
        logger.log(Level.INFO, () -> "Ship needs port repairs: id = " + ship.getId());
        journal.logNeedPortRepair(ship.getId());
        if (fleet.isAtPort(ship)) { // if ship is at port already just repair, dont assign task or log
            ship.repairTo(Ship.PORT_REPAIR_HEALTH);
        } else {
            Port nearestPort = fleet.findNearestPort(ship);
            if (nearestPort == null) {
                throw new FleetException("No port available for repairs for ship: id=" + ship.getId());
            }
            ship.assignTask(taskFactory.createRepairAtPortTask(nearestPort));
        }
    }

    @Override
    public void onTaskComplete(Ship ship, Task task) { 
        if (ship == null) {
            throw new FleetException("Null ship cannot complete task");
        }
        if (task == null) {
            throw new FleetException("Cannot complete null task");
        }
        logger.log(Level.INFO, () -> "Task completed by ship " + ship.getId() +  " -- " + task.toString());
        journal.logTaskCompleted(ship.getId(), task.toString());
    }

    @Override
    public void onRescueAbandoned(Ship ship, Task task) {
        if (ship == null) {
            throw new FleetException("Ship cannot be null onRescueAbandoned");
        }
        if (task == null) {
            throw new FleetException("Task cannot be null onRescueAbandoned");
        }
        RescueTask rescueTask;
        if (task.getTaskType() == TaskPriority.RESCUE) {
            rescueTask = (RescueTask)task;
            Ship disabledShip = rescueTask.getTargetShip();
            logger.log(Level.INFO, () -> "Rescue task abandoned for disabled ship " + disabledShip.getId());
            journal.logRescueAbandoned(ship.getId(), disabledShip.getId());
        }
    }

    /**
     * Event sent by tasks, as a ship will only move to port if on a supply or repair task
     * Can also create the task String in each task to accurately log the reason why the ship entered the port
     * Increments a port's docked counter
     */
    @Override
    public void onEnterPort(Ship ship, String task) {
        if (ship == null) {
            throw new FleetException("Null ship cannot enter port");
        }
        Port port = fleet.getPort(ship.getX(), ship.getY());
        if (port == null) {
            throw new FleetException("Ship entered port but no port found at position: x=" + ship.getX() + ", y=" + ship.getY());
        }
        ship.repairTo(Ship.PORT_REPAIR_HEALTH);
        ship.setDockedAtPort(true);
        port.shipDocked();
        logger.log(Level.INFO, () -> "Ship entered port: shipId=" + ship.getId() + ", portId=" + port.getId() + ", docked=" + port.getDockedCount());
        journal.logShipEnteredPort(ship.getId(), port.getId(), task);
    }

    /**
     * Task sent only by MovingShip
     * Decrements port's docked counter and logs exit
     */
    @Override
    public void onExitPort(Ship ship, int prevX, int prevY) {
        if (ship == null) {
            throw new FleetException("Null ship cannot exit port");         
        }
        if (prevX < 0 || prevY < 0) {
            throw new FleetException("Cannot exit port with negative coordinates");
        }
        Port port = fleet.getPort(prevX, prevY);
        if (port == null) {
            throw new FleetException("Ship exited port but no port found at position: x=" + ship.getX() + ", y=" + ship.getY());
        }
        port.shipUndocked();
        logger.log(Level.INFO, () -> "Ship exited port: shipId=" + ship.getId() + ", portId=" + port.getId() + ", docked=" + port.getDockedCount());
        journal.logShipExitPort(ship.getId(), port.getId());
    }

}
