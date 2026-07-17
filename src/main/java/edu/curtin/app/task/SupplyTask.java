package edu.curtin.app.task;

import edu.curtin.app.port.Port;
import edu.curtin.app.ship.LoadingShip;
import edu.curtin.app.ship.Ship;
import edu.curtin.app.ship.UnloadingShip;

// Task representing a supply run for cargo between two ports
// Supply task is completed after the ship unloads cargo to the destination port
public class SupplyTask extends Task {

    private Port pickupPort;
    private Port dropoffPort;

    private String cargo;
    private int tonnes;

    // Tracks whether cargo has been collected
    private boolean pickedUp = false;
    // Tracks whether cargo has been delivered
    private boolean delivered = false;

    public SupplyTask(Port pickupPort, Port dropoffPort, String cargo, int tonnes) {
        if (pickupPort == null || dropoffPort == null) {
            throw new TaskException("SupplyTask requires non-null ports");
        }
        if (cargo == null || cargo.isBlank()) {
            throw new TaskException("SupplyTask requires non-null cargo");
        }
        if (tonnes <= 0) {
            throw new TaskException("SupplyTask requires positive tonnes: " + tonnes);
        }
        this.pickupPort = pickupPort;
        this.dropoffPort = dropoffPort;
        this.cargo = cargo;
        this.tonnes = tonnes;
    } 

    public boolean hasPickedUp() {
        return pickedUp;
    }

    public boolean hasDelivered() {
        return delivered;
    }

    public void setPickedUp(boolean pickedUp) {
        this.pickedUp = pickedUp;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    @Override
    public int getTargetX() {
        return hasPickedUp() ? dropoffPort.getX() : pickupPort.getX();
    }

    @Override
    public int getTargetY() {
        return hasPickedUp() ? dropoffPort.getY() : pickupPort.getY();
    }

    // Before pickup, ship travel to the pickup port and transitions to LoadingShip
    // After loading cargo, ship is set to MovingShip()
    // When ship reaches destination port it transitions to UnloadingShip
    // After unloading, task is complete and state transition is handled within Ship
    @Override
    protected void performArrivalLogic(Ship ship) {
        if (hasPickedUp()) {
            ship.notifyEnterPort("unloading " + tonnes + " tonnes of " + cargo);
            ship.setState(new UnloadingShip(this));
        } else {
            ship.notifyEnterPort("loading " + tonnes + " tonnes of " + cargo);
            ship.setState(new LoadingShip(this));
        }
    }
    
    public void markPickedUp() {
        setPickedUp(true);
    }

    public void markDelivered() {
        setDelivered(true);
    }

    @Override
    public boolean canResume() {
        return true;
    }

    @Override
    public boolean shouldMarkComplete() {
        return hasPickedUp() && hasDelivered();
    }

    @Override
    public int getPriorityLevel() {
        return TaskPriority.SUPPLY_RUN.getValue();
    }

    @Override
    public TaskPriority getTaskType() {
        return TaskPriority.SUPPLY_RUN;
    }

    @Override
    public String toString() {
        return "Supply: shipping " + tonnes + " tonnes of " + cargo + 
            " from port " + pickupPort.getId() + " to port " + dropoffPort.getId();
    }
    
}
