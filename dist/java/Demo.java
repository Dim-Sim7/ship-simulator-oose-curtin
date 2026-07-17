package edu.curtin.oose.assignment2;

import java.io.*;

/**
 * Use this code to get started on Assignment 2. You are free to modify or replace this file as
 * needed (to fulfil the assignment requirements, of course).
 */
public class Demo
{
    public static void main(String[] args)
    {
        var inp = new Scenario();
        // inp.setErrorProbability(0.0);

        System.out.println("width = " + inp.getWidth() + ", height = " + inp.getHeight());
        try
        {
            while(System.in.available() == 0)
            {
                // ... ?

                // For illustration purposes -- this just prints out the messages as they come in.
                System.out.println("---");
                String msg = inp.nextMessage();
                while(msg != null)
                {
                    // System.out.println(msg);
                    System.out.println("\033[34;1m" + msg + "\033[m");
                    msg = inp.nextMessage();
                }

                // Wait 1 second
                try
                {
                    Thread.sleep(1000);
                }
                catch(InterruptedException e)
                {
                    throw new AssertionError(e);
                }
            }
        }
        catch(IOException e)
        {
            System.out.println("Error reading user input");
        }
    }
}
