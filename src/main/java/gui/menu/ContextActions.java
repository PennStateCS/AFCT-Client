package gui.menu;

import automata.State;
import automata.Transition;
import automata.turing.TuringMachineBuildingBlocks;
import gui.editor.*;
import gui.environment.Environment;
import gui.environment.Universe;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.io.File;

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

    public void addMenuItems(MenuElement menu, Tool tool, Point point, boolean showDefault) {
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
    }

    public JPopupMenu showPopupMenu(Tool tool, Point point, Component component, boolean showDefault) {
        JPopupMenu menu = new JPopupMenu();

        addMenuItems(menu, tool, point, showDefault);

        menu.show(component, point.x, point.y);
        return menu;
    }

    public void updateJMenu(JMenu menu, Tool currentTool, boolean showDefault) {
        menu.removeAll();
        addMenuItems(menu, currentTool, null, showDefault);
    }

    public static class DynamicJMenuListener implements MenuListener {
        Environment environment;
        JMenu menu;
        public DynamicJMenuListener(Environment environment, JMenu menu) {
            super();
            this.environment = environment;
            this.menu = menu;
        }

        @Override
        public void menuSelected(MenuEvent e) {
            //System.out.println("menuSelected");
            EditorPane ep = (EditorPane)environment.getActive();
            ep.getDrawer().contextActions.updateJMenu(menu, ep.getToolBar().getCurrentTool(), false);
        }

        @Override
        public void menuDeselected(MenuEvent e) {
            //System.out.println("menuDeselected");
        }

        @Override
        public void menuCanceled(MenuEvent e) {
            //System.out.println("menuCanceled");
        }
    }
}

