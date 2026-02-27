/*
 *  JFLAP - Formal Languages and Automata Package
 * 
 * 
 *  Susan H. Rodger
 *  Computer Science Department
 *  Duke University
 *  August 27, 2009

 *  Copyright (c) 2002-2009
 *  All rights reserved.

 *  JFLAP is open source software. Please see the LICENSE for terms.
 *
 */

package automata.turing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import automata.AutomatonSimulator;
import automata.Configuration;
import automata.SimulatorFactory;
import equivalence.TimeComplexityEnum;

public class TuringEq {
    public static final int NANO_PER_SECOND = 1000000000;

    // Amount of input strings that matched
    private int matching_count;

    // Amount of input strings where at least one tm didn't halt in time
    private int continue_count;

    // Tape contents of first tm on mismatch
    private TapeResult mismatchTape1;

    // Tape contents of second tm on mismatch
    private TapeResult mismatchTape2;

    // Input where the two Turing machines mismatch
    private String mismatchInput;

    // First Turing machine to compare
    private TuringMachine tm1;

    // Second Turing machine to compare
    private TuringMachine tm2;

    // Alphabet of the Turing machines
    private Set<String> alphabet;

    // Whether there is multiple tapes
    private boolean multiTape = false;

    // Which tape in a multiTape is the output tape
    private int outputTapeNum;

    // Which tape in a multiTape is the input tape
    private int inputTapeNum;

    private int inputsToGenerate;

    private double seconds_to_run;

    private long startTime;
    
    // number of threads to use. Should change from being hardcoded later. 
    private int numThreads = 4;

    // consumer threads to run EQ in parallel
    Thread[] consumers;

    // time complexity for running
    private TimeComplexityEnum timeComplexity = TimeComplexityEnum.LINEAR;

    /**
     * 
     * @param myself
     * @param other
     * 
     * @throws Exception
     */
    public TuringEq(TuringMachine myself, TuringMachine other) throws IncompatibleTMsException {
        this.tm1 = myself;
        this.tm2 = other;

        this.numThreads = Runtime.getRuntime().availableProcessors();
        // first, get both alphabets 
        // if they are different, union to check if they accept anything in (TM1 or TM2) - (TM1 and TM2)
        TuringAlphabetRetriever tmar = new TuringAlphabetRetriever();
        alphabet = new HashSet<>(Arrays.asList(tmar.getAlphabet(tm1)));
        Set<String> tm2alphabet = new HashSet<>(Arrays.asList(tmar.getAlphabet(tm2)));
        alphabet.addAll(tm2alphabet);

        if (myself.tapes() != other.tapes()) {
            throw new IncompatibleTMsException("TM Tape sizes differ");
        }

        multiTape = myself.tapes() > 1;
        inputTapeNum = 0;
        outputTapeNum = myself.tapes() - 1;
    }

    /** 
     * checks equivalence of Turing two turing machines 
     * @param matchTape whether to match the tape output if true, otherwise just match acceptance
     * @return true if no example showed they are not equivalent, false if they are not
    */
    public boolean checkEquivalence(boolean matchTape) {
        matching_count = 0;
        continue_count = 0;
        this.seconds_to_run = 5;
        BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
        inputsToGenerate = 1000;
        this.startTime = System.nanoTime();
        
        String[] actualAlphabet = alphabet.toArray(new String[alphabet.size()]);
        generateInputs(actualAlphabet, inputQueue, inputsToGenerate, null);
        
        String currentInput = inputQueue.remove();

        long curTime = System.nanoTime();
        long elapsedTime = curTime - startTime;

        // make new thread and run
        consumers = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            TuringEqConsumer consumer = new TuringEqConsumer(inputQueue, matchTape, this);
            consumers[i] = new Thread(consumer);
        }
        for (Thread consumer : consumers) {
            consumer.start();
        }

        while (elapsedTime < seconds_to_run * NANO_PER_SECOND) {
            MatchingEnum match;
            if (matchTape) {
                match = checkTapeEquivalence(currentInput);
            } else {
                match = checkStringEquivalence(currentInput);
            }
            
            if (null == match) {
                continue_count++;
            } else switch (match) {
                case DIFFERENT -> foundMismatchInput(currentInput);
                case EQUAL -> matching_count++;
                default -> continue_count++;
            }
            // act as producer and add to queue
            if (inputQueue.size() <= 100) {
                inputsToGenerate = inputsToGenerate * 10;
                generateInputs(actualAlphabet, inputQueue, inputsToGenerate, inputQueue.peek());
            }
            if (this.mismatchInput != null) {
                return false;
            }
            currentInput = inputQueue.remove();
            curTime = System.nanoTime();
            elapsedTime = curTime - startTime;
        }
        killThreads();
        return true;
    }

    /**
     * Thread found example that is mismatched. 
     * Sets mismatched string and kills threads
     */
    protected void foundMismatchInput(String input) {
        // set mismatch string if not set
        if (mismatchInput == null) {
            mismatchInput = input;
        }
        // kill all threads
        killThreads();
    }

    /**
     * kills all threads
     */
    private void killThreads() {
        for (Thread consumer : consumers) {
            consumer.interrupt();
        }
    }

    /**
     * For two Turing machines check if they give the same output on the same string
     * @return MatchingEnum if they match or not or if they didn't halt
     */
    protected MatchingEnum checkStringEquivalence(String input) {
        TapeResult tm1val = runTM(tm1, input, true);
        if (!tm1val.isHalted()) {
            return MatchingEnum.CONTINUE;
        }
        TapeResult tm2val = runTM(tm2, input, true);
        if (!tm2val.isHalted()) {
            return MatchingEnum.CONTINUE;
        }
        if (tm1val.isAccepted() == tm2val.isAccepted()) {
            return MatchingEnum.EQUAL;
        }
        return MatchingEnum.DIFFERENT;
    }

    /**
     * checks equivalence, both that the Turing machines accept the same inputs 
     * and that they leave the same values on the tape
     * @param input the input string
     * @return MatchingEnum if they match or not or if they didn't halt
     */
    protected MatchingEnum checkTapeEquivalence(String input) {
        TapeResult tm1val = runTM(tm1, input, false);
        if (!tm1val.isHalted()) {
            return MatchingEnum.CONTINUE;
        }
        TapeResult tm2val = runTM(tm2, input, false);
        if (!tm2val.isHalted()) {
            return MatchingEnum.CONTINUE;
        }
        if (multiTape) {
            tm1val.setOutputTapeNumber(outputTapeNum);
            tm2val.setOutputTapeNumber(outputTapeNum);
        }
        // For nondeterministic TMs. Need to create a set of all acceptance results and
        // only count as a result if it finishes going through all possibilities
        if (tm1.isNondeterministic() || tm2.isNondeterministic()) {
            Set<TMConfiguration> tm1acceptance = tm1val.getPossibleConfigs();
            Set<TMConfiguration> tm2acceptance = tm2val.getPossibleConfigs();
            Set<TMConfiguration> intersection = new HashSet<>(tm1acceptance);
            intersection.retainAll(tm2acceptance);
            if (!intersection.isEmpty()) {
                return MatchingEnum.EQUAL;
            }
        } else {
            if (tm1val.equals(tm2val)) {
                return MatchingEnum.EQUAL;
            }
        }
        mismatchTape1 = tm1val;
        mismatchTape2 = tm2val;
        return MatchingEnum.DIFFERENT;
    }

    /**
     * Gets the initial configurations for a Turing machine
     */
    private Configuration[] getInitConfigs(AutomatonSimulator sim, String input) {
        Configuration[] configs;
        switch (sim) {
            case NDTMSimulator ndtmSim -> {
                configs = ndtmSim.getInitialConfigurations(getInputArray(input));
                return configs;
            }
            case TMSimulator tmSim -> {
                configs = tmSim.getInitialConfigurations(getInputArray(input)); 
                return configs;
            }
            default -> {
            }
        }
        return null;
    }

    /**
     * Runs a Turing machine on an input
     * @param tm the Turing machine
     * @param input the input string
     * @param decider if true, gives boolean output, if false, gives tape output
     * @return whether it accepts, rejects, or fails to halt
     */
    private TapeResult runTM(TuringMachine tm, String input, boolean decider) {
        AutomatonSimulator sim = SimulatorFactory.getSimulator(tm);
        boolean nondeterministic = tm.isNondeterministic();
        long inputStartTime = System.nanoTime();
        double seconds_to_halt = 0.01;
        Configuration[] configs = getInitConfigs(sim, input);
        TapeResult result = new TapeResult(input, true, null);

        long curTime = System.nanoTime();
        long elapsedTime = curTime - inputStartTime;
        long max_time = getTimeToHalt(input, seconds_to_halt);
        // check return value of tm
        while (elapsedTime < max_time && (configs.length > 0)) {
			ArrayList<Configuration> next = new ArrayList<>();
            for (Configuration config : configs) {
                if (config.isAccept()) {
                    if (nondeterministic && !decider) {
                        result.addAcceptanceConfig((TMConfiguration) config);
                    } else {
                        return new TapeResult(input, true, (TMConfiguration) config);
                    }

                } else {
                    next.addAll(sim.stepConfiguration(config));
                }
            }
			configs = (Configuration[]) next.toArray(new Configuration[0]);
            curTime = System.nanoTime();
            elapsedTime = curTime - inputStartTime;
        }
        if (nondeterministic && !result.getPossibleConfigs().isEmpty()) { // nondeterminism found a result
            if (elapsedTime >= seconds_to_halt * NANO_PER_SECOND) {
                return new TapeResult(input, false, null); // did not run out of configurations
            }
            return result;
        }
        if (elapsedTime >= seconds_to_halt * NANO_PER_SECOND) {
            return new TapeResult(input, false, null);
        }
        else {
            return new TapeResult(input, true, null);
        }
    }

    /**
     * Generates inputs to test the Turing machine on
     * Alphanumerically adds possibilities of the characters, starting from lastGenerated, to a queue
     * Generates more words than togenerate to get all of a certain length
     * @param alphabet union alphabet of Turing machines 
     * @param inputQueue Queue to add the inputs to
     * @param num_to_generate Number of inputs to generate
     * @param lastGenerated The last string generated to build off of
     */
    protected void generateInputs(String[] alphabet, BlockingQueue<String> inputQueue, int num_to_generate, String lastGenerated) {
        if (lastGenerated == null) {
            lastGenerated = "";
        }
        if (alphabet.length < 1) {
            return; // protect against infinite loop
        }
        int num_generated = 0;
        int word_length = lastGenerated.length() + 1;
        long curTime = System.nanoTime();
        
        long elapsedTime = curTime - this.startTime;

        // keep looping at different lengths until we generate enough characters
        while (num_generated < num_to_generate && elapsedTime < seconds_to_run * NANO_PER_SECOND) {
            System.out.println("Generating inputs of size: " + word_length);
            int level_size = (int) Math.pow(alphabet.length, word_length);
            num_generated += level_size;
            StringBuilder[] new_characters = new StringBuilder[level_size];
            for (int i = 0; i < new_characters.length; i++) {
                new_characters[i] = new StringBuilder(); // initialize all to ""
            }
            // build list of all strings one character at a time
            for (int char_pos = 0; char_pos < word_length; char_pos++) {
                for (int i = 0; i < level_size; i++) {
                    String toAdd = alphabet[(int) Math.floor(i / Math.pow(3, char_pos)) % alphabet.length];
                    new_characters[i].append(toAdd);
                }
            }
            for (StringBuilder str : new_characters) {
                try {
                    inputQueue.put(str.toString());
                } catch(InterruptedException e) {
                    System.err.println(e.getMessage());
                }
            }
            word_length += 1;
            
            curTime = System.nanoTime();
            elapsedTime = curTime - this.startTime;
        }
    }

    /**
     * Generates input array for multiple tapes based on the generated input
     * @param input input string for the input tape
     * @return String array of inputs for both tapes
     */
    private String[] getInputArray(String input) {
        int tapes = tm1.tapes();
        String[] inputArray = new String[tapes];
        Arrays.fill(inputArray, ""); 
        inputArray[inputTapeNum] = input;
        return inputArray;
    }

    /**
     * Get the time to halt
     * @param input input string
     * @param seconds_to_halt how many seconds to take to halt
     * @return time to halt, as a long
     */
    private long getTimeToHalt(String input, double seconds_to_halt) {
        long inputTime = input.length();
        if (null != this.timeComplexity) // none of these actually used yet, just linear
        switch (this.timeComplexity) {
            case QUADRATIC -> inputTime = (long) Math.pow(inputTime, 2);
            case LOGARITHMIC -> inputTime = (long) Math.log(inputTime);
            case EXPONENTIAL -> inputTime = (long) Math.pow(2, inputTime);
            default -> {
            }
        }
        return (long) (inputTime * seconds_to_halt * NANO_PER_SECOND);
    }

    /**
     * Sets the time complexity to give longer inputs more time
     * @param time time complexity to use
     */
    public void setTimeComplexity(TimeComplexityEnum time) {
        this.timeComplexity = time;
    }

    public int getMatching_count() {
        return matching_count;
    }

    public int getContinue_count() {
        return continue_count;
    }

    public void incrementMatching() { matching_count++; }

    public void incrementContinue() { continue_count++; }

    public int getinputsToGenerate() { return inputsToGenerate; }

    public void setInputsToGenerate(int n) {inputsToGenerate = n;}

    public TapeResult getMismatchTape1() {
        return mismatchTape1;
    }

    public TapeResult getMismatchTape2() {
        return mismatchTape2;
    }

    public String getMismatchInput() {
        return mismatchInput;
    }

    public void removeFromAlphabet(String s) {
        alphabet.remove(s);
    }

    public void addToAlphabet(String s) {
        alphabet.add(s);
    }

    public String[] getAlphabet() {
        return alphabet.toArray(new String[alphabet.size()]);
    }

    public void setOutputTapeNum(int newNum) {
        outputTapeNum = newNum;
    }

    public void setInputTapeNum(int newNum) {
        inputTapeNum = newNum;
    }
}
