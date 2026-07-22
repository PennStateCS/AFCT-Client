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

package gui.editor;

import automata.State;
import automata.Transition;
import automata.gnfa.GNFATransition;
import gui.viewer.AutomatonPane;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

/**
 * This is a transition creator for generalized nondeterministic finite automata (GNFA) states
 *
 * @author Teddy FitzPatrick
 */
public class GNFATransitionCreator extends TableTransitionCreator {
    public GNFATransitionCreator(AutomatonPane parent){super(parent);}

    protected Transition initTransition(State from, State to) {
        return new GNFATransition(from, to, "");
    }

    protected TableModel createModel(Transition transition) {
        final GNFATransition t = (GNFATransition) transition;
        return new AbstractTableModel() {
            private static final long serialVersionUID = 1L;

            public Object getValueAt(int row, int column) {
                return s;
            }

            public void setValueAt(Object o, int r, int c) {
                s = (String) o;
            }

            public boolean isCellEditable(int r, int c) {
                return true;
            }

            public int getRowCount() {
                return 1;
            }

            public int getColumnCount() {
                return 1;
            }

            public String getColumnName(int c) {
                return "Label";
            }

            String s = t.getLabel();
        };
    }

    public Transition modifyTransition(Transition t, TableModel model) {
        //EDebug.print("ModifyTransitionCalled");
        String s = (String) model.getValueAt(0, 0);
        try {
            return new GNFATransition(t.getFromState(), t.getToState(), s);
        } catch (IllegalArgumentException e) {
            reportException(e);
            return null;
        }
    }
}
