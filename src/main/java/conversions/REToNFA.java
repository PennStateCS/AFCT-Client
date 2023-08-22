package conversions;

import java.awt.Point;
import java.io.File;
import java.io.Serializable;

import automata.State;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;
import file.XMLCodec;
import gui.environment.RegularEnvironment;
import gui.environment.Universe;
import gui.regular.ConvertToAutomatonPane;
import gui.regular.REToFSAController;
import regular.Discretizer;
import regular.RegularExpression;

public class REToNFA {
    public static void convert(File input, File output) {
	XMLCodec codec = new XMLCodec();
	RegularExpression re = (RegularExpression)codec.decode(input, null);
	FiniteStateAutomaton nfa = new FiniteStateAutomaton();
	State initialState = nfa.createState(new Point(60, 40));
	State finalState = nfa.createState(new Point(450, 250));
	String transString = Discretizer.delambda(re.asString().replace('!', Universe.curProfile.getEmptyString().charAt(0)));
	FSATransition initialTransition = new FSATransition(initialState, finalState, transString);

	nfa.setInitialState(initialState);
	nfa.addFinalState(finalState);
	nfa.addTransition(initialTransition);

	RegularEnvironment reEnv = new RegularEnvironment(re);
	ConvertToAutomatonPane convPane = new ConvertToAutomatonPane(reEnv);
	REToFSAController controller = new REToFSAController(convPane, nfa);

	controller.completeAll();
	codec.encode(nfa, output, null);
    }

    public static void main(String[] args) {
	File input = new File(args[0]);
	File output = new File(args[1]);

	REToNFA.convert(input, output);
    }
}
