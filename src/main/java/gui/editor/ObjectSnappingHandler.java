package gui.editor;

import automata.Automaton;
import automata.State;
import gui.viewer.AutomatonPane;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class ObjectSnappingHandler {
    private Integer cX;
    private Integer cY;
    private Integer xSnapping;
    private Integer ySnapping;
    private int avgX;
    private int avgY;

    private int xOffset = 0;
    private int yOffset = 0;
    // TODO: make this changeable through a GUI menu
    private int snappingEpsilon = 10;
    private boolean multiSnapping = false;
    private boolean snapByDefault = true;

    public ObjectSnappingHandler() {

    }

    public boolean getSnapByDefault() {
        return snapByDefault;
    }

    public void setSnapByDefault(boolean snapByDefault) {
        this.snapByDefault = snapByDefault;
    }

    public void setSnappingIndicators (AutomatonPane view, Integer xSnapping, Integer ySnapping) {
        view.getDrawer().setXSnappingIndicator(xSnapping);
        view.getDrawer().setYSnappingIndicator(ySnapping);
    }

    public void showSnappingIndicators(AutomatonPane view) {
        setSnappingIndicators(view, xSnapping, ySnapping);
    }

    public void clearSnappingIndicators(AutomatonPane view) {
        setSnappingIndicators(view, null, null);
    }

    public boolean whenMouseDragged(MouseEvent event, State[] states, Point initialPointClick, Automaton automaton, State newState) {
        Point p = event.getPoint();

        int count = 0;
        avgX = 0;
        avgY = 0;
        if (newState == null) {
            for (State state : states) {
                if (state.isSelected()) {
                    count++;
                    avgX += state.getPoint().x;
                    avgY += state.getPoint().y;
                }
            }
            if (count != 0) {
                avgX /= count;
                avgY /= count;
            }
        } else {
            count = 1;
            avgX += newState.getPoint().x;
            avgY += newState.getPoint().y;
        }
        avgX += p.x - initialPointClick.x;
        avgY += p.y - initialPointClick.y;

        Automaton.XYPair bestXY = automaton.getClosestXY(avgX, avgY);
        cX = bestXY.x;
        cY = bestXY.y;

        xSnapping = null;
        ySnapping = null;
        boolean doSnapping = snapByDefault;
        if ((event.getModifiersEx() & InputEvent.ALT_DOWN_MASK) > 0) {
            doSnapping = !doSnapping;
        }

        if (count > 1 && !multiSnapping) {
            doSnapping = false;
        }

        //System.out.printf("xCoords = %s\n", getAutomaton().xCoords.toString());
        //System.out.printf("yCoords = %s\n\n", getAutomaton().yCoords.toString());
        return doSnapping;
    }

    public boolean whenMouseDragged(MouseEvent event, State[] states, Point initialPointClick, Automaton automaton) {
        return whenMouseDragged(event, states, initialPointClick, automaton, null);
    }

    public Point snapState(int x, int y) {
        // Snap to x-aligned states
        if (cY != null) {
            if (Math.abs(avgY - cY) + Math.abs(yOffset) <= snappingEpsilon) {
                //System.out.printf("avgY = %d, cY = %d\n", avgY, cY);
                yOffset += avgY - cY;
                int offset = y - avgY;
                y = cY;// + offset;
                ySnapping = cY;
            } else {
                y += yOffset;
                yOffset = 0;
            }
        }

        // Snap to y-aligned states
        if (cX != null) {
            if (Math.abs(avgX - cX) + Math.abs(xOffset) <= snappingEpsilon) {
                //System.out.printf("avgX = %d, cX = %d\n", avgX, cX);
                xOffset += avgX - cX;
                int offset = avgX - x;
                x = cX;// + offset;
                xSnapping = cX;
            } else {
                x += xOffset;
                xOffset = 0;
            }
        }

        return new Point(x, y);
    }
}
