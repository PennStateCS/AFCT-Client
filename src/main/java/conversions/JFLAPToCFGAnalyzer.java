package conversions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import file.ParseException;
import file.XMLCodec;
import grammar.Grammar;
import grammar.GrammarChecker;
import grammar.Production;

public class JFLAPToCFGAnalyzer {
    public static void convertGrammar(File input, PrintStream output) {
        XMLCodec x = new XMLCodec();     // to decode the file

        try {
            Serializable object = x.decode(input, null);

            if(object instanceof Grammar) {
                Grammar g = (Grammar)object;
                ArrayList<Production> productions = new ArrayList<Production>(Arrays.asList(g.getProductions()));
		ArrayList<String> lhss = new ArrayList<String>();
                String lastLHS = null;

		for (Production p : productions) {
		    if (lhss.indexOf(p.getLHS()) == -1) {
			lhss.add(p.getLHS());
		    }
		}

                productions.sort(new Comparator<Production>()  {
                        @Override
                        public int compare(Production p1, Production p2) {
			    return Integer.compare(lhss.indexOf(p1.getLHS()), lhss.indexOf(p2.getLHS()));
                        }
                    });

                if(GrammarChecker.isContextFreeGrammar(g)) {
                    for (Production p : productions) {
                        String lhs = " : ";

                        if (!p.getLHS().equals(lastLHS)) {
                            lastLHS = p.getLHS();
                            lhs = lastLHS + " : ";
                        }

                        output.println(lhs + quoteRHSTerminals(p) + ";");
                    }
                }
                else {
                    System.out.println("Not a CFG");
                }
            }
        }
	catch (ParseException pe) {
            pe.printStackTrace();
        }
    }

    private static String quoteRHSTerminals(Production p) {
        String[] terminals = p.getTerminals();
	ArrayList<String> rhs = new ArrayList<String>();

        for (String s : p.getSymbolsOnRHS()) {
            boolean isTerm = false;

            for (String t : terminals) {
                if (t.equals(s)) {
                    isTerm = true;
                    break;
                }
            }

            if (isTerm) {
                rhs.add("\"" + s + "\"");
            }
            else {
		rhs.add(s);
            }
        }

        return String.join(" ", rhs);
    }

    public static void main(String[] args) {
	File input = new File(args[0]);

	convertGrammar(input, System.out);
    }
}
