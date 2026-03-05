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


package automata.gnfa;

import automata.State;
import automata.Transition;
import gui.environment.Universe;

/**
 * ...
 *
 * @see automata.gnfa.GNFA
 *
 * @author Teddy FitzPatrick
 */
public class GNFATransition extends Transition {
    private static final long serialVersionUID = 1L;
    public GNFATransition(State from, State to, String label){
        super(from, to);
        setLabel(label);
    }
    public Transition copy(State from, State to) {return new automata.gnfa.GNFATransition(from, to, myLabel);}

    public String getLabel(){return myLabel;}

    protected void setLabel(String label){myLabel = label;}

    public String getDescription() {
        String desc = getLabel();
        if (desc.isEmpty())
            return Universe.curProfile.getEmptyString(); // I am a badass.
        return getLabel();
    }

    public String toString() {
        return super.toString() + ": \"" + getLabel() + "\"";
    }

    public boolean equals(Object object) {
        try {
            automata.gnfa.GNFATransition t = (automata.gnfa.GNFATransition) object;
            return super.equals(t) && myLabel.equals(t.myLabel);
        } catch (ClassCastException e) {
            return false;
        }
    }

    public int hashCode() {
        return super.hashCode() ^ myLabel.hashCode();
    }

    protected String myLabel = "";
}
