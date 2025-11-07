package gui.menu;

import automata.State;
import automata.Transition;
import automata.turing.TuringMachineBuildingBlocks;
import gui.editor.*;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import javax.swing.*;
import java.awt.*;

public class ContextActions {
    protected AutomatonPane view;
    protected AutomatonDrawer drawer;
    private StateContextMenu stateContextMenu;
    private TransitionContextMenu transitionContextMenu;
    private DefaultContextMenu defaultContextMenu;

    public ContextActions(AutomatonPane view, AutomatonDrawer drawer) {
        this.view = view;
        this.drawer = drawer;

        stateContextMenu = new StateContextMenu(view, drawer);
        transitionContextMenu = new TransitionContextMenu(view, drawer);
        defaultContextMenu = new DefaultContextMenu(view, drawer);
    }

    public JPopupMenu showPopupMenu(Tool tool, Point point, Component component, boolean showDefault) {
        JPopupMenu menu = new JPopupMenu();
        boolean stateContext = false;
        boolean transitionContext = false;

        if (!showDefault) {
            State[] states = drawer.getAutomaton().getStates();

            for (State state : states) {
                if (state.isSelected()) {
                    stateContext = true;
                    break;
                }
            }

            if (stateContext) {
                boolean skipFinal = tool instanceof MealyArrowTool;
                boolean isTuringBlock = drawer.getAutomaton() instanceof TuringMachineBuildingBlocks;
                boolean allowOnlyFinal = false;
                if (tool instanceof ArrowTool) {
                    allowOnlyFinal = ((ArrowTool) tool).shouldAllowOnlyFinalStateChange();
                }
                stateContextMenu.addMenuItems(menu, skipFinal, isTuringBlock, allowOnlyFinal);
                stateContextMenu.selectAndEnableMenuItems(states);
            }

            /*
            Transition[] transitions = drawer.getAutomaton().getTransitions();
            for (Transition transition : transitions) {
                if (transition.isSelected) {
                    transitionContext = true;
                    break;
                }
            }
            if (transitionContext) {

            }
            */
        }

        if (showDefault || (!stateContext && !transitionContext)) {
            boolean displayOnly = tool instanceof ArrowDisplayOnlyTool;
            defaultContextMenu.addMenuItems(menu, displayOnly);
            defaultContextMenu.selectAndEnableMenuItems(point);
        }

        menu.show(component, point.x, point.y);
        return menu;
    }
}

