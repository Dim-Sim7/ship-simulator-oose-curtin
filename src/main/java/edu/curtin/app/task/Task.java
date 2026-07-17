package edu.curtin.app.task;

import edu.curtin.app.ship.Ship;
/**
 * Base class for all ship tasks -- Implemented using the Template method pattern
 * Subclasses implement arrival logic and completion checks
 * Tasks: RepairAtPortTask, RescueTask, SupplyTask
 */
public abstract class Task {

    private boolean complete = false;

    public boolean isComplete() {
        return complete;
    }

    public void markComplete() {
        complete = true;
    }

    /**
     * Called when a ship reaches the task destination
     * Subclasses perform task-specific behaviour and determine
     * whether the task should now be marked complete
     */
    public void onArrival(Ship ship) {
        performArrivalLogic(ship);   // delegate to subclasses
        if (shouldMarkComplete()) {
            markComplete();
        }
    }

    /**
     * Returns whether the task is still valid
     * Subclasses can override this for dynamic tasks
     * such as rescues which may become invalid during transit
     */
    public boolean isStillValid() {
        return true;
    }

    /**
     * A disabled ship cannot resume a rescue mission once it itself has been rescued
     * Tasks where they are heading to a port can be resumed
     */
    public abstract boolean canResume();

    public abstract int getTargetX();
    public abstract int getTargetY();

    // Priority level used for scheduling
    public abstract int getPriorityLevel();
    // Returns the task category/type
    public abstract TaskPriority getTaskType();

    // Task-specific behaviour performed on arrival
    protected abstract void performArrivalLogic(Ship ship);
    
    // Determines whether the task should complete after arrival
    public abstract boolean shouldMarkComplete();

    @Override
    public abstract String toString();

}
