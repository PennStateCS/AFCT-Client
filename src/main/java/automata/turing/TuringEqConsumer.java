/*
 *  JFLAP - Formal Languages and Automata Package
 * 
 * 
 *  Susan H. Rodger
 *  Computer Science Department
 *  Duke University
 *  August 27, 2009

 *  Copyright (c) 2002-2009
 *  All rights reserved.

 *  JFLAP is open source software. Please see the LICENSE for terms.
 *
 */

package automata.turing;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;

import static automata.turing.TuringEq.NANO_PER_SECOND;

/**
 * Consumer class to run TuringEq in parallel to handle more inputs
 */
public class TuringEqConsumer implements Runnable{

    protected BlockingQueue<String> inputQueue = null;
    private boolean matchTape;
    private TuringEq eq;

    public TuringEqConsumer(BlockingQueue<String> queue, boolean matchTape, TuringEq eq) {
        this.inputQueue = queue;
        this.matchTape = matchTape;
        this.eq = eq;
    }

    @Override
    public void run() {
        try {
            long startTime = System.nanoTime();
            long curTime = System.nanoTime();
            long elapsedTime = curTime - startTime;
            String currentInput = inputQueue.take();
            double seconds_to_run = 5;
            
            while (elapsedTime < seconds_to_run * NANO_PER_SECOND) {
                if (Thread.interrupted()) {
                    return;
                }
                MatchingEnum match;
                if (matchTape) {
                    match = eq.checkTapeEquivalence(currentInput);
                } else {
                    match = eq.checkStringEquivalence(currentInput);
                }
                
                if (match == MatchingEnum.DIFFERENT) {
                    eq.foundMismatchInput(currentInput);
                } else if (match == MatchingEnum.EQUAL) {
                    eq.incrementMatching();
                } else {
                    eq.incrementContinue();
                }
                // wait for inputs on the queue from main thread
                wait_For_input();
                try {
                    currentInput = inputQueue.remove();
                } catch (NoSuchElementException e) {
                    // e.printStackTrace();
                    wait_For_input();
                }
                
                curTime = System.nanoTime();
                elapsedTime = curTime - startTime;
            }
        } catch (InterruptedException e) {
            System.err.println("Thread interrupted");
            // e.printStackTrace();
        }
    }

    /**
     * Waits for input queue to have some inputs ready
     */
    private void wait_For_input() {
        try {
            int wait_time = 5; // ms
            while (inputQueue.size() <= 10) {
                if (Thread.interrupted()) {
                    return;
                }
                Thread.sleep(wait_time);
                wait_time = wait_time * 2;
            }
        } catch (InterruptedException e) {
            System.err.println("Thread interrupted while awaiting input");
            // e.printStackTrace();
        }
    }
}