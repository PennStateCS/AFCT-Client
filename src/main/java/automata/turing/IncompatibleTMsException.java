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

/**
 * This class is an exception that is thrown in the event incompatible <CODE>Turing Machines</CODE> are compared
 * 
 * @see automata.turing
 * @author Lucas Famous
 */

public class IncompatibleTMsException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

    public IncompatibleTMsException(String message)
    {
        super(message);
    }

}
