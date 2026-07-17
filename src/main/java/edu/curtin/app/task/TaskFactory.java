package edu.curtin.app.task;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.curtin.app.port.Port;
import edu.curtin.app.ship.Ship;

/**
 * Simple factory to create different concrete tasks
 */
public class TaskFactory {
    private static final Logger logger = Logger.getLogger(TaskFactory.class.getName());
    
    public Task createSupplyTask(Port pickupPort, Port dropoffPort, String cargo, int tonnes) {
        if (pickupPort == null) {
            throw new TaskException("Pickup port cannot be null");
        }
        if (dropoffPort == null) {
            throw new TaskException("Drop off port cannot be null");
        }
        if (pickupPort.equals(dropoffPort)) {
            throw new TaskException("Pickup and dropoff prot cannot be the same position");
        }
        if (cargo == null || cargo.isBlank()) {
            throw new TaskException("Cargo cannot be null");
        }
        if (tonnes <= 0) {
            throw new TaskException("Tonnes must be positive: " + tonnes);
        }
        logger.log(Level.INFO, () -> "Supply task created: cargo=" + cargo + ", tonnes=" + tonnes 
            + ", from=" + pickupPort.getId() + ", to=" + dropoffPort.getId());
        return new SupplyTask(pickupPort, dropoffPort, cargo, tonnes);
    }

    public Task createRescueTask(Ship targetShip) {
        if (targetShip == null) {
            throw new TaskException("Target ship cannot be null");
        }
        if (targetShip.getHealth() > Ship.DISABLED_HEALTH) {
            throw new TaskException("Target ship does not need rescue: health=" + targetShip.getHealth());
        }
        logger.log(Level.INFO, () -> "Rescue task created for ship: id=" + targetShip.getId());
        return new RescueTask(targetShip);
    }

    public Task createRepairAtPortTask(Port port) {
        if (port == null) {
            throw new TaskException("Port position cannot be null");
        }
        logger.log(Level.INFO, () -> "Repair at port task created: portId=" + port.getId());
        return new RepairAtPortTask(port);
    }
}
