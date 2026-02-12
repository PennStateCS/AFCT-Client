package gui.editor;

import automata.Automaton;
import automata.State;
import gui.viewer.AutomatonDrawer;

import java.awt.*;
import java.awt.event.MouseEvent;

public class StateOverlap {
    public final static int STATE_RADII = 20;
    public final static int STATE_DIAMETER = STATE_RADII * 2;

    /**
     * Prevents overlapping states by adding a positional offset.
     * The offset is calculated based on the relative angle, making it seem intuitive and natural.
     * Enumerates every possible set of overlapping states, guaranteeing distinguishability.
     * @param automaton automaton to fix state overlap
     */
    public static void handleStateOverlap(Automaton automaton){
        State[] states = automaton.getStates();
        for (State state : states){
            Point statePoint = state.getPoint();
            double stateY = statePoint.getY();
            double stateX = statePoint.getX();
            for (State otherState : states){
                if (state == otherState) continue;  // doesn't matter that states overlap with themselves
                // Calculate the distance between the two states
                Point otherStatePoint = otherState.getPoint();
                double otherStateY = otherStatePoint.getY();
                double otherStateX = otherStatePoint.getX();
                double deltaY = otherStateY - stateY;
                double deltaX = otherStateX - stateX;
                // Only handle states that are within distance of the state's diameter
                double distance = Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
                if (distance >= STATE_DIAMETER) continue;
                // Use the angle between the two states to calculate a proper offset
                double angle = Math.atan2(deltaY, deltaX);
                double offsetDistance = STATE_DIAMETER - distance;
                double offsetY = offsetDistance * Math.sin(angle);
                double offsetX = offsetDistance * Math.cos(angle);
                otherState.getPoint().setLocation(otherStateX + offsetX, otherStateY + offsetY);
            }
        }
    }

}
