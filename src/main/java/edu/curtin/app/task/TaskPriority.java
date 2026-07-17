package edu.curtin.app.task;

/**
 * Task priority levels: lower value = higher priority.
 * RESCUE is highest: a disabled ship must be recovered before supply runs continue
 * https://www.baeldung.com/java-enum-values#bd-enum-class Used this resource to
 * find out how to attach values to enums
 */

public enum TaskPriority {
    RESCUE(1),
    SUPPLY_RUN(2),
    REPAIR_AT_PORT(3);

    private final int value;

    TaskPriority(int value) { 
        this.value = value; 
    }

    public int getValue() { 
        return value; 
    }
}