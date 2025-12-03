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





package gui.environment;

import gui.pumping.PumpingLemmaChooser;

/**
 * An environment for {@link pumping.PumpingLemma}s. 
 * 
 * @author Jinghui Lim
 *
 */
public class PumpingLemmaEnvironment extends Environment 
{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PumpingLemmaEnvironment(PumpingLemmaChooser lemma) 
    {
        super(lemma);
    }

    //TODO
    @Override
    public void handleDelete() {

    }

    @Override
    public void handleDuplicate(boolean shiftHeld) {

    }

    @Override
    public void handleCopy() {

    }

    @Override
    public void handleCut() {

    }

    @Override
    public void handlePaste(boolean shiftHeld) {

    }

    @Override
    public void handleUndo() {

    }

    @Override
    public void handleRedo() {

    }

    @Override
    public void handleSelectAll() {

    }
}
