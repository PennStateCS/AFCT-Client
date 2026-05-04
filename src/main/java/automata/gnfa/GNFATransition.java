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
 * A <CODE>GNFATransition</CODE> is a <CODE>Transition</CODE> object with
 * a label field, which is a string representation of a regular expression.
 * The regular expression label is used to determine if the automaton should
 * transition between states on this transition.
 *
 * @see automata.gnfa.GNFA
 *
 * @author Teddy FitzPatrick
 */
public class GNFATransition extends Transition {
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new <CODE>GNFATransition</CODE> object.
     *
     * @param from
     *            the state this transition comes from
     * @param to
     *            the state this transition goes to
     * @param label
     *            the label for this transition, a regular expression
     *            that matches a prefix-substring of the unprocessed
     *            input to transition the automaton between states
     */
    public GNFATransition(State from, State to, String label){
        super(from, to);
        setLabel(label);
    }

    /**
     * {@inheritDoc}
     */
    public Transition copy(State from, State to) {return new automata.gnfa.GNFATransition(from, to, myLabel);}

    /**
     * Returns the label for this transition.
     */
    public String getLabel(){return myLabel;}

    /**
     * Sets the label for this transition.
     * The label is expected to be a valid regular expression.
     *
     * @param label
     *            the new label for this transition
     * @throws IllegalArgumentException
     *             if the label contains any "bad" characters, i.e., not
     *             alphanumeric
     */
    protected void setLabel(String label){myLabel = label;}

    /**
     * Returns the description for this transition.
     *
     * @return for GNFAs, like FSAs, the description is just the label
     */
    public String getDescription() {
        String desc = getLabel();
        if (desc.isEmpty())
            return Universe.curProfile.getEmptyString(); // I am a badass.
        return getLabel();
    }

    /**
     * {@inheritDoc}
     * @see GNFATransition#getLabel()
     */
    public String getDescriptionWithSpacesHandled() {
        return getEmptyOrReplaceSpaces(getDescription());
    }

    /**
     * Returns a string representation of this object. This is the same as the
     * string representation for a regular transition object, with the label
     * (regular expression) tacked on.
     *
     * @see automata.Transition#toString
     * @return a string representation of this object
     */
    public String toString() {
        return super.toString() + ": \"" + getLabel() + "\"";
    }

    /**
     * {@inheritDoc}
     * @see Transition#equals(Object)
     */
    public boolean equals(Object object) {
        try {
            automata.gnfa.GNFATransition t = (automata.gnfa.GNFATransition) object;
            return super.equals(t) && myLabel.equals(t.myLabel);
        } catch (ClassCastException e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * @see Transition#hashCode()
     */
    public int hashCode() {
        return super.hashCode() ^ myLabel.hashCode();
    }

    /**
     * The label for this transition: a valid regular expression.
     * A prefix substring of the unprocessed input must match the label's
     * regular expression for the automaton to transition between states.
     */
    protected String myLabel = "";
}
