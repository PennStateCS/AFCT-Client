package gui.action;

import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;
import automata.graph.AutomatonGraph;
import automata.graph.LayoutAlgorithm;
import automata.graph.LayoutAlgorithmFactory;
import automata.graph.layout.RandomLayoutAlgorithm;
import automata.graph.layout.VertexMover;
import gui.environment.Environment;
import gui.environment.Universe;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.StringJoiner;

/**
 * This class is an experimental addon to the main JFLAP application intended for research purposes. The class
 * houses all the logic and methods needed to automatically generate automata and then screenshot and save
 * them to a corresponding .jff file. The functionality is exposed as one of the toolbar options, where the
 * user can specify how many training examples they want and where they wish the examples to be saved.
 *
 * @author Chang you Yu
 */

public class GenerateOCRDataAction extends RestrictedAction{
    final int MIN_NUMBER_STATES = 3;
    final int MAX_NUMBER_STATES = 9;
    final int MAX_NUMBER_TRANSITIONS = 11;

    // These values are some magic numbers stolen from layoutAlgorithmAction.java
    // Do we really even need to take these into account?
    private final int assumedUsedWidth = 25;
    private final int assumedUsedHeight = 100;

    enum layoutProcess {
        THE_RANDOM_ALGORITHM,
        THE_RANDOM_ALGORITHM_THEN_GEM,
        CIRCLE,
        TWO_CIRCLE,
        TREE_THEN_GEM,
        FLIP_OVER_VERTICAL
    }

    private FiniteStateAutomaton automaton = null;
    private Environment environment = null;

    /**
     * Constructor
     * @param automaton the empty FSA that is first generated upon initialization
     * @param environment the enviornment
     */
    public GenerateOCRDataAction(FiniteStateAutomaton automaton,
                                 Environment environment) {
        super("Generate data", null);
        this.automaton = automaton;
        this.environment = environment;
    }

    /**
     * Main loop the data generation process where a specified number
     * of automata are generated, screenshotted, and paired up with their XML descriptions.
     *
     * @param event the action event
     */
    public void actionPerformed(ActionEvent event) {
        int numExamples = 0;
        try {
            numExamples = Integer.parseInt(
                    JOptionPane.showInputDialog("How many examples would you like to generate?", "enter a number")
            );
        } catch (Exception err) {
            JOptionPane.showMessageDialog(null, "Error: please enter a number");
        }
        String folderPath;
        try {
            folderPath = getFolderToSaveTo();
        } catch (Exception err) {
            JOptionPane.showMessageDialog(null, err.getMessage());
            return;
        }


        for (int i = 0; i < numExamples; i++) {
            // add some random number of states
            int numStatesForThisExample = initializeStates();

            State[] states = this.automaton.getStates();
            createStartAndEndStates(numStatesForThisExample, states);

            // add transitions
            int numTransitions = createTransitions(numStatesForThisExample, states);

            // remove orphaned states
            removeOrphanedStates(this.automaton);

            // check that the automaton still has states after pruning
            if (this.automaton.getStates().length < 1) {
                this.automaton.clear();
                continue;
            }

            // choose a layout algorithm
            layoutProcess algorithmCode = chooseLayoutProcess(numStatesForThisExample, numTransitions);

            // apply the algorithm
            applyLayoutAlgorithm(algorithmCode);

            // In the case that the initial state is more on the right side, mirror the automaton
            // across the vertical axis because real life people probably don't put the initial
            // state to the right.
            ensureInitialStateOnLeftSide(this.automaton);

            // Move the automaton to be more in the center of frame
            moveToCenter(this.automaton);

            // save the automaton as a .jff file
            File savedFilePath = null;
            try {
                savedFilePath = saveToFile(i, numStatesForThisExample, numTransitions, algorithmCode, folderPath);
            } catch (Exception ex) {
                String errorMessage = "Error: " + ex;
                JOptionPane.showMessageDialog(null, errorMessage);
            }

            // screenshot the diagram and save it as a png
            // we will assume if we made it here the filepath is good
            saveAutomatonAsPNG(savedFilePath);

            // reset the automata to be empty for the next run unless we are at the end of the loop
            if (i != numExamples-1) {
                this.automaton.clear();
            }
        }
    }

    private int initializeStates() {
        // create from 3 to 11 states
        int numStatesForThisExample = (int) (Math.random()*(MAX_NUMBER_STATES+1-MIN_NUMBER_STATES))+MIN_NUMBER_STATES;
        for (int k = 0; k < numStatesForThisExample; k++) {
            // we will use the layout algorithms later to move the states around
            // to be in a more logical position
            this.automaton.createState(new Point(100, 50*k));
        }
        return numStatesForThisExample;
    }

    private void createStartAndEndStates(int numStatesForThisExample, State[] states) {
        // randomly assign the start and ending states
        int startStateID = (int) (Math.random()*numStatesForThisExample);
        State startState = this.automaton.getStateWithID(startStateID);
        this.automaton.setInitialState(startState);

        // pick which states will be final states
        int numFinalStates = (int) (Math.random() * (numStatesForThisExample/3)) + 1;
        HashSet<Integer> finalStateIDs = new HashSet<>();
        for (int k = 0; k < numFinalStates; k++) {
            int id = (int) (Math.random() * numStatesForThisExample);
            while(finalStateIDs.contains(id)) {
                id = (int) (Math.random() * numStatesForThisExample);
            };
            finalStateIDs.add(id);
        }
        finalStateIDs.forEach((id -> this.automaton.addFinalState(states[id])));
    }

    private int createTransitions(int numStatesForThisExample, State[] states) {
        // randomly add some transitions
        // for every state, give it a chance to have a transition to the
        // other states.
        int numTransitions = 0;
        for (int k = 0; k < numStatesForThisExample; k++) {
            for (int j = 0; j < numStatesForThisExample; j ++) {

                double chance = Math.random();
                // we will make a transition roughly 33% of the time
                if (chance < 0.33 && numTransitions < MAX_NUMBER_TRANSITIONS) {
                    String label = getRandomAlphaString();
                    // TODO: change how transition rendering looks for OCR data generation
                    for (int i = 0; i < 1; i++) {
                        Transition t = new FSATransition(states[k],states[j], Character.toString(label.charAt(i)));
                        this.automaton.addTransition(t);
                        numTransitions++;
                    }
                }
            }
        }
        return numTransitions;
    }

    private String getRandomAlphaString() {
        Random random = new Random();
        StringBuilder result = new StringBuilder();
        String alphaPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

        int numSymbols = random.nextInt(3) + 1;

        for (int i = 0; i < numSymbols; i++) {
            int index = random.nextInt(alphaPool.length());
            result.append(alphaPool.charAt(index));
        }

        return result.toString();
    }

    private layoutProcess chooseLayoutProcess(int numStatesForThisExample, int numTransitions) {
        // first see if we should just use GEM if we have a high number of states/transitions
        if (numStatesForThisExample > 4 || numTransitions > 8) {
            return layoutProcess.THE_RANDOM_ALGORITHM_THEN_GEM;
        }

        // otherwise pick between doing the random algorithm, the random algorithm then
        // doing GEM, or randomly picking from the other algorithms
        double chance = Math.random();

        if (chance < 0.4) {
            return layoutProcess.THE_RANDOM_ALGORITHM;
        } else if (chance < 0.8) {
            return layoutProcess.THE_RANDOM_ALGORITHM_THEN_GEM;
        } else if (chance < 0.87 ) {
            return layoutProcess.CIRCLE;
        } else if (chance < 0.94) {
            return layoutProcess.TWO_CIRCLE;
        } else {
            return layoutProcess.TREE_THEN_GEM;
        }
    }

    private void applyLayoutAlgorithm(layoutProcess algorithm) {
        LayoutAlgorithm layoutAlgorithm;
        AutomatonGraph graph;

        Dimension psize = new Dimension(this.environment.getWidth()-assumedUsedWidth,
                this.environment.getHeight()-assumedUsedHeight);
        Dimension vertexDimension = new Dimension(30,30);
        int vertexBuffer = 120;
        switch (algorithm) {
            case THE_RANDOM_ALGORITHM:
                graph = LayoutAlgorithmFactory.getAutomatonGraph(LayoutAlgorithmFactory.RANDOM, this.automaton);
                layoutAlgorithm = LayoutAlgorithmFactory.getLayoutAlgorithm(
                        LayoutAlgorithmFactory.RANDOM, psize,
                        vertexDimension,
                        vertexBuffer
                );
                layoutAlgorithm.layout(graph, null);
                graph.moveAutomatonStates();
                break;
            case THE_RANDOM_ALGORITHM_THEN_GEM:
                graph = LayoutAlgorithmFactory.getAutomatonGraph(LayoutAlgorithmFactory.RANDOM, this.automaton);
                // first do the random algorithm
                layoutAlgorithm = LayoutAlgorithmFactory.getLayoutAlgorithm(
                        LayoutAlgorithmFactory.RANDOM, psize,
                        vertexDimension,
                        vertexBuffer
                );
                layoutAlgorithm.layout(graph, null);
                graph.moveAutomatonStates();

                // then do GEM
                layoutAlgorithm = LayoutAlgorithmFactory.getLayoutAlgorithm(
                        LayoutAlgorithmFactory.GEM, psize,
                        vertexDimension,
                        vertexBuffer
                );
                layoutAlgorithm.layout(graph, null);
                graph.moveAutomatonStates();
                break;
            case CIRCLE:
                graph = LayoutAlgorithmFactory.getAutomatonGraph(LayoutAlgorithmFactory.CIRCLE, this.automaton);
                layoutAlgorithm = LayoutAlgorithmFactory.getLayoutAlgorithm(
                        LayoutAlgorithmFactory.CIRCLE, psize,
                        vertexDimension,
                        vertexBuffer
                );
                layoutAlgorithm.layout(graph, null);
                graph.moveAutomatonStates();
                break;
            case TWO_CIRCLE:
                graph = LayoutAlgorithmFactory.getAutomatonGraph(LayoutAlgorithmFactory.TWO_CIRCLE, this.automaton);
                layoutAlgorithm = LayoutAlgorithmFactory.getLayoutAlgorithm(
                        LayoutAlgorithmFactory.TWO_CIRCLE, psize,
                        vertexDimension,
                        vertexBuffer
                );
                layoutAlgorithm.layout(graph, null);
                graph.moveAutomatonStates();
                break;
            case TREE_THEN_GEM:
                graph = LayoutAlgorithmFactory.getAutomatonGraph(LayoutAlgorithmFactory.TREE_DEGREE, this.automaton);
                layoutAlgorithm = LayoutAlgorithmFactory.getLayoutAlgorithm(
                        LayoutAlgorithmFactory.TREE_DEGREE, psize,
                        vertexDimension,
                        vertexBuffer
                );
                layoutAlgorithm.layout(graph, null);

                layoutAlgorithm = LayoutAlgorithmFactory.getLayoutAlgorithm(
                        LayoutAlgorithmFactory.GEM, psize,
                        vertexDimension,
                        vertexBuffer
                );
                layoutAlgorithm.layout(graph, null);
                graph.moveAutomatonStates();
                break;
            case FLIP_OVER_VERTICAL:
                graph = LayoutAlgorithmFactory.getAutomatonGraph(VertexMover.VERTICAL_CENTER, this.automaton);
                layoutAlgorithm = LayoutAlgorithmFactory.getLayoutAlgorithm(
                        VertexMover.VERTICAL_CENTER,
                        psize,
                        vertexDimension,
                        vertexBuffer
                );
                layoutAlgorithm.layout(graph, null);
                graph.moveAutomatonStates();
                break;
        }
    }

    /**
     * Pops up a file selection window for the user to select where they want
     * to save their synthetic data to.
     * @return String representing the absolute file path to the user selected
     * directory
     * @throws Exception throws an exception if no folder was selected
     */
    private String getFolderToSaveTo() throws Exception{
        String folderYouWantToSaveTo = null;
        Universe.CHOOSER.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int response = Universe.CHOOSER.showOpenDialog(null);
        if (response == JFileChooser.APPROVE_OPTION) {
            // Get the file and save the path to a String variable
            File selectedFile = Universe.CHOOSER.getSelectedFile();
            folderYouWantToSaveTo = selectedFile.getAbsolutePath();
        } else {
            throw new Exception("No folder location selected");
        }

        return folderYouWantToSaveTo;
    }

    // save the automaton as a .jff file
    private File saveToFile(int index,
                            int numStatesForThisExample,
                            int numTransitions,
                            layoutProcess algorithmCode,
                            String folderYouWantToSaveTo) {
        String completeFilePath = String.format("%s%sSample%d_S%d_T%d-%s",
                folderYouWantToSaveTo,
                File.separator,
                index,
                numStatesForThisExample,
                numTransitions,
                algorithmCode
        );
        File fileToSaveTo = new File(completeFilePath);
        Universe.CHOOSER.setFileSelectionMode(JFileChooser.FILES_ONLY);
        return Universe.frameForEnvironment(this.environment).saveForOCRData(fileToSaveTo);
    }

    private void saveAutomatonAsPNG(File file) {
        Component somePane = environment.tabbed.getSelectedComponent();
        SaveGraphUtility.saveGraphUsingExistingFile(somePane, file);
    }

    private void moveToCenter(FiniteStateAutomaton automaton) {
        State[] states = automaton.getStates();

        for (State s : states) {
            Point point = s.getPoint();
            // these are just magic numbers that subjectively give good results
            Point newPoint = new Point(point.x+200, point.y+20);
            s.setPoint(newPoint);
        }
    }

    /**
     * In cases where the initial state is more on the right side of the automaton,
     * this method will flip the automaton over the vertical axis so that the initial
     * state is always more on the left side. When to flip is determined by calculating
     * the average X position of all the states, and then checking if the X position
     * of the initial state is greater than the average.
     * @param automaton The automaton in which you want flipped if the inital state
     *                  is more to the right.
     */
    private void ensureInitialStateOnLeftSide(FiniteStateAutomaton automaton) {
        int averageX = 0;
        State[] states = automaton.getStates();
        for (State s : states) {
            averageX += s.getPoint().x;
        }

        // yes we are doing integer division, but we don't need that much precision
        averageX /= states.length;
        State initial = automaton.getInitialState();

        // if the initial state is over the average X, flip the automaton so that
        // the initial state is more on the left side
        if (initial.getPoint().x > averageX) {
            applyLayoutAlgorithm(layoutProcess.FLIP_OVER_VERTICAL);
        }
    }

    /**
     * Removes any state in an automaton that has no transitions going out
     * or into the state.
     * @param automaton The automaton in which you want orphaned states to
     *                  be pruned.
     */
    private void removeOrphanedStates(FiniteStateAutomaton automaton) {
        State[] states = automaton.getStates();
        boolean haveToChooseInitialState = false;
        for (State s : states) {
            int inDegree = automaton.getTransitionsToState(s).length;
            int outDegree = automaton.getTransitionsFromState(s).length;
            int degree = inDegree + outDegree;

            // if this is an orphaned state
            if (degree < 1) {
                if (automaton.isInitialState(s)) {
                    haveToChooseInitialState = true;
                }
                automaton.removeState(s);
            }
        }

        states = automaton.getStates();
        if (haveToChooseInitialState && states.length > 0) {
            int rand = (int)(Math.random() * states.length);
            automaton.setInitialState(states[rand]);
        }
    }
}

