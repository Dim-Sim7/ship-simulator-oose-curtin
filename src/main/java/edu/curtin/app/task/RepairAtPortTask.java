package edu.curtin.app.task;

import edu.curtin.app.port.Port;
import edu.curtin.app.ship.Ship;

// Task that sends an idle ship to the closest port for repairs
public class RepairAtPortTask extends Task {

    // Port where the ship will travel for repairs
    private final Port port;

    public RepairAtPortTask(Port port) {
        if (port == null) {
            throw new TaskException("RepairAtPortTask requires a non-null port");
        }
        this.port = port;
    }

    @Override
    public int getTargetX() {
        return port.getX();
    }

    @Override
    public int getTargetY() {
        return port.getY();
    }

    @Override
    protected void performArrivalLogic(Ship ship) {
        ship.notifyEnterPort("repair");
    }

    @Override
    public boolean canResume() {
        return true;
    }

    @Override
    public boolean shouldMarkComplete() {
        return true;
    }

    @Override
    public int getPriorityLevel() {
        return TaskPriority.REPAIR_AT_PORT.getValue();
    }

    @Override
    public TaskPriority getTaskType() {
        return TaskPriority.REPAIR_AT_PORT;
    }

    @Override
    public String toString() {
        return "Repaired at port: " + port.getId();
    }
}