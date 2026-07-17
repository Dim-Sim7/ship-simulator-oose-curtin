package edu.curtin.app.message;


/**
 * Strategy interface for parsing scenario messages
 * Each strategy handles one message type (ship, port, storm and supply)
 */
public interface ParseStrategy {
    boolean canParse(String[] message);
    void parse(String[] message) throws MessageException;
}

