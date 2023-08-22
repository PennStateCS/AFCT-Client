package conversions;

import java.awt.Point;
import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import automata.State;
import automata.Transition;
import automata.pda.PDATransition;
import automata.pda.PushdownAutomaton;
import file.XMLCodec;
import grammar.Production;
import grammar.cfg.ContextFreeGrammar;

import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;

public class PDAToCFG {
    public static HashSet<Character> stackAlphabet(PushdownAutomaton pda) {
        HashSet<Character> gamma = new HashSet<Character>();
        Transition[] transitions = pda.getTransitions();

        for (Transition t : transitions) {
            char[] pushSymbols = ((PDATransition)t).getStringToPush().toCharArray();

            for (char c : pushSymbols) {
                gamma.add(c);
            }
        }

        return gamma;
    }

    public static void setupPDA(PushdownAutomaton pda) {
        State newInitState = pda.createState(new Point(0,0));
        State origInitState = pda.getInitialState();
        State newFinalState = pda.createState(new Point(0,0));
        State[] origFinalStates = pda.getFinalStates();
        HashSet<Character> gamma = stackAlphabet(pda);
        String bottomOfStack = new String(new int[] {(gamma.isEmpty() ? 64 : Collections.max(gamma)) + 1}, 0, 1);
        PDATransition initTrans = new PDATransition(newInitState, origInitState, "", "", bottomOfStack);
        PDATransition finalTrans = new PDATransition(newFinalState, newFinalState, "", bottomOfStack, "");

        pda.setInitialState(newInitState);
        pda.addTransition(initTrans);

        for (State s : origFinalStates) {
            pda.removeFinalState(s);
            pda.addTransition(new PDATransition(s, newFinalState, "", "", ""));
        }

        for (char g : gamma) {
            pda.addTransition(new PDATransition(newFinalState, newFinalState, "", Character.toString(g), ""));
        }

        pda.addTransition(finalTrans);
        pda.addFinalState(newFinalState);
        fixTransitions(pda, bottomOfStack);
    }

    public static void fixTransitions(PushdownAutomaton pda, String bottomOfStack) {
        Transition[] transitions = pda.getTransitions();
        ArrayList<PDATransition> brokenTransitions = new ArrayList<PDATransition>();

        for (Transition t : transitions) {
            PDATransition pdat = (PDATransition)t;

            if (pdat.getStringToPush() != "") {
                if (pdat.getStringToPop() != "") {
                    State popManyState = pda.createState(new Point(0, 0));
                    PDATransition popTransition = new PDATransition(pdat.getFromState(), popManyState, pdat.getInputToRead(), pdat.getStringToPop(), "");
                    PDATransition pushTransition = new PDATransition(popManyState, pdat.getToState(), "", "", pdat.getStringToPush());

                    pda.addTransition(popTransition);
                    pda.addTransition(pushTransition);
                    popManySymbols(popTransition);
                    pushManySymbols(pushTransition);
                    brokenTransitions.add(pdat);
                }
                else if (pdat.getStringToPush().length() > 1) {
                    pushManySymbols(pdat);
                    brokenTransitions.add(pdat);
                }
            }
            else if (pdat.getStringToPop() != "") {
                if (pdat.getStringToPop().length() > 1) {
                    popManySymbols(pdat);
                    brokenTransitions.add(pdat);
                }
            }
            else {
                State lambdaState = pda.createState(new Point(0, 0));
                PDATransition pushTransition = new PDATransition(pdat.getFromState(), lambdaState, pdat.getInputToRead(), "", bottomOfStack);
                PDATransition popTransition = new PDATransition(lambdaState, pdat.getToState(), "", bottomOfStack, "");

                pda.addTransition(pushTransition);
                pda.addTransition(popTransition);
                brokenTransitions.add(pdat);
            }
        }

        for (PDATransition t : brokenTransitions) {
            pda.removeTransition(t);
        }
    }

    public static void popManySymbols(PDATransition t) {
        String popString = t.getStringToPop();

        if (t.getStringToPush().length() > 0) {
            throw new IllegalArgumentException("Transition " + t + " pushes symbols");
        }

        if (popString.length() > 1) {
            PushdownAutomaton pda = (PushdownAutomaton)t.getAutomaton();
            State from = t.getFromState();
            State to = t.getToState();
            State newFrom = pda.createState(new Point(0, 0));
            PDATransition fromTransition = new PDATransition(from, newFrom, t.getInputToRead(), Character.toString(popString.charAt(popString.length() - 1)), "");
            PDATransition toTransition = new PDATransition(newFrom, to, "", popString.substring(0, popString.length() - 1), "");

            pda.addTransition(fromTransition);
            pda.addTransition(toTransition);
            pda.removeTransition(t);
            popManySymbols(toTransition);
        }
    }

    public static void pushManySymbols(PDATransition t) {
        String pushString = t.getStringToPush();

        if (t.getStringToPop().length() > 0) {
            throw new IllegalArgumentException("Transition " + t + " pops symbols");
        }

        if (pushString.length() > 1) {
            PushdownAutomaton pda = (PushdownAutomaton)t.getAutomaton();
            State from = t.getFromState();
            State to = t.getToState();
            State newFrom = pda.createState(new Point(0, 0));
            PDATransition fromTransition = new PDATransition(from, newFrom, t.getInputToRead(), "", Character.toString(pushString.charAt(pushString.length() - 1)));
            PDATransition toTransition = new PDATransition(newFrom, to, "", "", pushString.substring(0, pushString.length() - 1));

            pda.addTransition(fromTransition);
            pda.addTransition(toTransition);
            pda.removeTransition(t);
            pushManySymbols(toTransition);
        }
    }

    public static ContextFreeGrammar transformPDA(PushdownAutomaton pda) {
	UnicodeSet variables = new UnicodeSet("[:Lu:]");
	UnicodeSetIterator varIt = new UnicodeSetIterator(variables);
        ContextFreeGrammar cfg = new ContextFreeGrammar();
        ArrayList<State> states = new ArrayList<State>(Arrays.asList(pda.getStates()));
        HashMap<Point, String> statePairToVar = new HashMap<Point, String>();
        Transition[] transitions = pda.getTransitions();
        HashMap<State, ArrayList<PDATransition>> pushTransitionsByState = new HashMap<State, ArrayList<PDATransition>>();
        HashMap<State, ArrayList<PDATransition>> popTransitionsByState = new HashMap<State, ArrayList<PDATransition>>();

        if (pda.getFinalStates().length != 1) {
            throw new IllegalArgumentException("PDA does not have a unique final state");
        }

        for (State s : states) {
            pushTransitionsByState.put(s, new ArrayList<PDATransition>());
            popTransitionsByState.put(s, new ArrayList<PDATransition>());
        }

        for (Transition t : transitions) {
            PDATransition pdat = (PDATransition)t;

            if (pdat.getStringToPush() != "") {
                if (pdat.getStringToPop() != "") {
                    throw new IllegalArgumentException("Transition " + pdat.toString() + " pushes and pops symbols in the stack");
                }

                if (pdat.getStringToPush().length() > 1) {
                    throw new IllegalArgumentException("Transition " + pdat.toString() + " pushes more than one symbol into the stack");
                }

                pushTransitionsByState.get(t.getFromState()).add(pdat);
            }
            else if (pdat.getStringToPop() != "") {
                if (pdat.getStringToPop().length() > 1) {
                    throw new IllegalArgumentException("Transition " + pdat.toString() + " pops more than one symbol from the stack");
                }

                popTransitionsByState.get(t.getFromState()).add(pdat);
            }
            else {
                throw new IllegalArgumentException("Transition " + pdat.toString() + "neither pushes nor pops symbols in the stack");
            }
        }

        for (int i = 0; i < states.size(); i++) {
            for (int j = 0; j < states.size(); j++) {
		if (!varIt.next()) {
		    throw new RuntimeException("Out of valid grammar variables");
		}

		// Not all uppercase characters are upper case...
		while (!Character.isUpperCase(varIt.getString().charAt(0))) {
		    if (!varIt.next()) {
			throw new RuntimeException("Out of valid grammar variables");
		    }
		}

                statePairToVar.put(new Point(i, j), varIt.getString());
            }
        }

	if (!varIt.next()) {
	    throw new RuntimeException("Out of valid grammar variables");
	}

	// Not all uppercase characters are upper case...
	while (!Character.isUpperCase(varIt.getString().charAt(0))) {
	    if (!varIt.next()) {
		throw new RuntimeException("Out of valid grammar variables");
	    }
	}

        cfg.addProduction(new Production(varIt.getString(), statePairToVar.get(new Point(states.indexOf(pda.getInitialState()), states.indexOf(pda.getFinalStates()[0])))));

        for (int i = 0; i < states.size(); i++) {
            cfg.addProduction(new Production(statePairToVar.get(new Point(i, i)), ""));
        }

        for (int i = 0; i < states.size(); i++) {
            for (int j = 0; j < states.size(); j++) {
                for (int k = 0; k < states.size(); k++) {
                    cfg.addProduction(new Production(statePairToVar.get(new Point(i, k)), statePairToVar.get(new Point(i,j)) + statePairToVar.get(new Point(j,k))));
                }
            }
        }

        for (int p = 0; p < states.size(); p++) {
            for (PDATransition tpush : pushTransitionsByState.get(states.get(p))) {
                for (int s = 0; s < states.size(); s++) {
                    for (PDATransition tpop : popTransitionsByState.get(states.get(s))) {
                        if (tpush.getStringToPush().equals(tpop.getStringToPop())) {
                            int q = states.indexOf(tpop.getToState());
                            int r = states.indexOf(tpush.getToState());
                            String a = tpush.getInputToRead();
                            String b = tpop.getInputToRead();

                            cfg.addProduction(new Production(statePairToVar.get(new Point(p, q)), a + statePairToVar.get(new Point(r, s)) + b));
                        }
                    }
                }
            }
        }

        return cfg;
    }

    public static void convert(File input, File output, File debug) {
        XMLCodec codec = new XMLCodec();
        PushdownAutomaton pda = (PushdownAutomaton)codec.decode(input, null);

        setupPDA(pda);

	if (debug != null) {
	    codec.encode(pda, debug, null);
	}

	codec.encode(transformPDA(pda), output, null);
    }

    public static void main(String[] args) {
        File input = new File(args[0]);
        File output = new File(args[1]);

        if (args.length > 2) {
            File debug = new File(args[2]);

	    PDAToCFG.convert(input, output, debug);
        }
	else {
	    PDAToCFG.convert(input, output, null);
	}
    }
}
