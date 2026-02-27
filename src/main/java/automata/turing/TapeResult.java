package automata.turing;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class TapeResult {
    private final boolean halted;
    private final String input;
    private TMConfiguration acceptanceConfig;
    private Integer outputTapeNum;
    private HashSet<TMConfiguration> possibleAcceptanceConfigs;

    /**
     * Create a TapeResult object for a Turing machine
     * @param input Input string that gave this result
     * @param halted Whether the TM halted
     * @param acceptanceConfig What the acceptance configuration it reached was, if applicable
     */
    public TapeResult(String input, boolean halted, TMConfiguration acceptanceConfig) {
        this.input = input;
        this.halted = halted;
        this.acceptanceConfig = acceptanceConfig;
        this.possibleAcceptanceConfigs = new HashSet<TMConfiguration>();
        if (acceptanceConfig != null) {
            this.possibleAcceptanceConfigs.add(acceptanceConfig);
        }
    }
    
    public String getInput() {
        return input;
    }

    public boolean isHalted() {
        return halted;
    }

    public boolean isAccepted() {
        if (this.acceptanceConfig != null) {
            return acceptanceConfig.isAccept();
        }
        return false;
    }

    public void setOutputTapeNumber(int num) {
        this.outputTapeNum = num;
    }

    /**
     * Add a new Acceptance config. To be used when checking tape values for a nondeterministic TM
     * @param newConfig Additional possible acceptance configuration
     */
    public void addAcceptanceConfig(TMConfiguration newConfig) {
        if (this.acceptanceConfig == null) {
            this.acceptanceConfig = newConfig;
        }
        this.possibleAcceptanceConfigs.add(newConfig);
    }

    public HashSet<TMConfiguration> getPossibleConfigs() {
        return this.possibleAcceptanceConfigs;
    }

    /**
     * Get the contents of all tapes in an acceptance configuration
     * @return Set containing the contents of all tapes in the given acceptance configuration
     */
    public Set<String> getOutputTapevals() {
        if (acceptanceConfig == null) {
            return Collections.emptySet();
        }
        Tape[] tapes = acceptanceConfig.getTapes();
        if (outputTapeNum != null) {
            tapes = new Tape[]{tapes[outputTapeNum]};
        }
        
        Set<String> tapevals = Arrays.asList(tapes)
            .stream()
            .map(Tape::getContents)
            .map(TapeResult::cutOffBlanks)
            .collect(Collectors.toSet());
        return tapevals;
    }

    /**
     * Removes blank characters from the start and end of a tape string
     * @param string The string of the tape
     * @return new String with blanks cut off from each end
     */
    public static String cutOffBlanks(String string) {
        char blank = Tape.BLANK;
        char[] stringCharacters = string.toCharArray();
        StringBuilder strbuild = new StringBuilder(string);
        int firstCharIndex = 0;
        int lastCharIndex = 0;
        for (int i = 0; i < stringCharacters.length; i++) {
            if (stringCharacters[i] != blank) {
                firstCharIndex = i;
                break;
            }
        }
        lastCharIndex = firstCharIndex;
        for (int i = stringCharacters.length - 1; i > firstCharIndex; i--) {
            if (stringCharacters[i] != blank) {
                lastCharIndex = i;
                break;
            }
        }
        strbuild.delete(0, firstCharIndex);
        strbuild.delete(lastCharIndex - firstCharIndex, stringCharacters.length);
        return strbuild.toString();
    }

    @Override
    public boolean equals(Object object) {
		try {
			TapeResult tr = (TapeResult) object;
            if (tr.isAccepted() != this.isAccepted()) {
                return false;
            }
			return this.getOutputTapevals().equals(tr.getOutputTapevals());
		} catch (ClassCastException e) {
			return false;
		}
    }
}
