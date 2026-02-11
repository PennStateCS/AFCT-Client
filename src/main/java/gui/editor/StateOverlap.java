package gui.editor;

import automata.Automaton;
import automata.State;
import gui.viewer.AutomatonDrawer;

import java.awt.*;
import java.awt.event.MouseEvent;

public class StateOverlap {
    public final static int OVERLAP_OFFSET = 40;
    public static void handleStateOverlap(MouseEvent event, AutomatonDrawer drawer, Automaton automaton){
        // Prevent overlapping states if an automata state was moved on mouse release
        State movedState = drawer.stateAtPoint(event.getPoint());
        if (movedState == null) return;
        // Repeatedly apply an offset until the state no longer overlaps another states
        while (overlappingAnotherState(movedState, automaton)){
            // Apply the offset
            Point movedStatePoint = movedState.getPoint();
            double movedStateX = movedStatePoint.getX();
            double movedStateY = movedStatePoint.getY();
            movedStatePoint.setLocation(movedStateX + OVERLAP_OFFSET, movedStateY);
        }
    }

    public static boolean overlappingAnotherState(State state, Automaton automaton){
        Point statePoint = state.getPoint();
        State[] states = automaton.getStates();
        for (State otherState: states){
            if (state == otherState) continue;
            // check if the state overlaps another
            Point otherStatePoint = otherState.getPoint();
            if (statePoint.equals(otherStatePoint)) return true;
        }
        return false;
    }
}
