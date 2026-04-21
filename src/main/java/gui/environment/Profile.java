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




package gui.environment;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBoxMenuItem;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import gui.Globals;
import gui.editor.IconKeeper;
import gui.menu.SettingsMenu;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import file.xml.DOMPrettier;
import gui.editor.TMTransitionCreator;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static gui.Globals.getPreferencesFilePath;
import static gui.environment.Universe.settingsMenu;

public class Profile {
    // TODO: combine LAMBDA with lambda, and EPSILON with epsilon
    public static final String LAMBDA = "\u03BB";     // Jinghui Lim added stuff
    public static final String EPSILON = "\u03B5";    // see MultipleSimulateAction
	public static final String lambda = "\u03BB";
	public static final String epsilon = "\u03B5";
	public static final String lambdaText = "u03BB";
	public static final String epsilonText = "u03B5";
    public static final String Z_PDA_STACK_BOTTOM_MARKER = "Z";
    public static final String DOLLAR_SIGN_PDA_STACK_BOTTOM_MARKER = "$";
    public static String PDA_STACK_BOTTOM_MARKER = DOLLAR_SIGN_PDA_STACK_BOTTOM_MARKER;
    public enum transitionRendering {
        STACKONTOP,
        COMMADELINIATEDLIST,
    }

	public String Color = "Original";
	//public int undo_num = 50;
	public int undo_num = -1;

	/** The tag name for the empty string preference. */
	public static final String EMPTY_STRING_NAME = "empty_string";

	/** The tag name for the root of a structure. */
	public static final String STRUCTURE_NAME = "structure";

	/** The tag name for the type of structure this is. */
	public static final String STRUCTURE_TYPE_NAME = "type";
	
	/** The tag name for the out from final state preference. */
	public static final String TURING_FINAL_NAME = "turing_final";
	
	/** The tag name for the Undo amount preference. */
	public static final String UNDO_AMOUNT_NAME = "undo_amount";

    /**The tag name for accept by final state preference*/
    public static final String ACCEPT_FINAL_STATE = "turing_accept_by_final_state";

    /**The tag name for accept by halting preference*/
    public static final String ACCEPT_HALT = "turing_accept_by_halt";

    /**The tag name for allow-stay preference.*/
    public static final String ALLOW_STAY = "turing_allow_stay_on_transition";

    /**The tag name for legacy icons preference.*/
    public static final String LEGACY_ICONS = "legacy_use_legacy_icons";

    /**The tag name for legacy icons preference.*/
    public static final String LEGACY_SUBMISSION_GUI = "legacy_use_legacy_submission_gui";

    /**The tag name for the first state auto initial state feature.*/
    public static final String AUTO_INITIAL_STATE = "auto_set_first_state_as_initial_state";


    /* Settings */

    private String emptyString = epsilon;

	/**
	 * Determines whether transitions can be issued from the final
	 * state of a Turing machine.
	 * 
	 * @author Chris Morgan
	 */
	private boolean turingTransFromFinal;

    //default to acceptByFinalState, since that was how it used to be
    private boolean turingAcceptByFinalState; //I would rather have it a better way, but I'm short on Time - ~Henry
    private boolean turingAcceptByHalting; //I would rather have it a better way, but I'm short on Time - ~Henry
    private boolean turingAllowStay; //default to true since that was the old implementation

    /**
     * Flag to keep track of if in this environment we should automatically
     * set the first state placed down to be the initial state. This is
     * just for quality of life reasons.
     */
    // TODO: make it possible to override this setting for individual environments (i.e. individual editor windows)
    //   This will likely be part of the planned update to include a preferences menu on editor windows,
    //      not just the main window.
    private boolean autoInitialState;

    /**
     * Setting for how transition labels should be rendered.
     */
    private transitionRendering transitionsRenderedAs;

    /**
     * Legacy options
     */
    private boolean legacyUseLegacyIcons;
    private boolean legacyUseLegacySubmissionGui;


    public Path pathToFile;
	
    public void setNumUndo(int nn){
    	undo_num = nn;
        settingsMenu.getSetUndoAmountAction().updateActionText();
    }

    public int getNumUndo() {
        return undo_num;
    }
	
	public Profile(){
        // Set default emptyString character to epsilon
		emptyString = epsilon;

        // Turing Machine Settings
		turingTransFromFinal = false;
        turingAcceptByFinalState = true; //default to true, since that was the status before;
        turingAcceptByHalting = false; //defaults to false, since it was not in previous JFLAP
        turingAllowStay = false; //defaults to false temporarily since that's how it was before

        // Legacy Settings
        legacyUseLegacyIcons = false;
        legacyUseLegacySubmissionGui = false;

        // Other settings
        autoInitialState = true;
        transitionsRenderedAs = transitionRendering.STACKONTOP;
	}
	
	/**
	 * Sets the empty string.
	 * 
	 * @param empty the empty string
	 */
	public void setEmptyString(String empty){
		emptyString = empty;
	}
	
	/**
	 * Returns the empty string.
	 * 
	 * @return the empty string
	 */
	public String getEmptyString(){
		return emptyString;
	}
	
	/**
	 * Sets the color.
	 * 
	 * @param color the new color
	 */
	public void setColor(String color) {
		Color = color;
	}
	
	/**
	 * Returns the current color.	
	 * @return the current color
	 */
	public String getColor() {
		return Color;
	}
	
	/**
	 * Sets whether transitions leading from Turing machine final states are allowed.
	 * 
	 * @param t whether the transitions are allowed
	 */
	public void setTransitionsFromTuringFinalStateAllowed(boolean t) {
		turingTransFromFinal = t;
		settingsMenu.getTMPrefs().turingTransitionsFromFinalStateCheckBox.setSelected(t);
	}
	
	/**
	 * Sets whether Turing machines will accept by final state.
	 * 
	 * @param t yes or no
	 */
	public void setAcceptByFinalState(boolean t) {
		turingAcceptByFinalState = t;
        settingsMenu.getTMPrefs().turingAcceptByFinalStateCheckBox.setSelected(t);
	}
	/**
	 * Sets whether Turing machines will accept by halting.
	 * 
	 * @param t yes or no
	 */
	public void setAcceptByHalting(boolean t) {
		turingAcceptByHalting = t;
        settingsMenu.getTMPrefs().turingAcceptByHaltingCheckBox.setSelected(t);
	}

	/**
	 * Sets whether Turing machines will accept by halting.
	 * 
	 * @param t yes or no
	 */
	public void setAllowStay(boolean t) {
		turingAllowStay = t;
        settingsMenu.getTMPrefs().turingAllowStayCheckBox.setSelected(t);
        TMTransitionCreator.setDirs(t);
	}

    public void setUseLegacyIcons(boolean t) {
        legacyUseLegacyIcons = t;
        settingsMenu.getLegacyOptions().legacyUseLegacyIconsCheckBox.setSelected(t);
    }

    public void setUseLegacySubmissionGui(boolean t) {
        legacyUseLegacySubmissionGui = t;
        settingsMenu.getLegacyOptions().legacyUseLegacySubmissionGuiCheckBox.setSelected(t);
    }

    public boolean getUseLegacySubmissionGui() {
        return legacyUseLegacySubmissionGui;
    }

    public boolean getLegacyUseLegacyIcons() {
        return legacyUseLegacyIcons;
    }

	/**
	 * Returns whether transitions from Turing machine final states are allowed.
	 * 
	 * @return whether the transitions are allowed from final states
	 */
	public boolean transitionsFromTuringFinalStateAllowed() {
		return turingTransFromFinal;
	}
	
    public boolean getAcceptByFinalState(){
        return turingAcceptByFinalState;
    }

    public boolean getAcceptByHalting(){
        return turingAcceptByHalting;
    }

    public boolean getAllowStay(){
        return turingAllowStay;
    }


    public void setAutoInitialState(boolean t) {
        autoInitialState = t;
        settingsMenu.getAutoInitialStateCheckBox().setSelected(t);
    }

    public boolean getAutoInitialState() {
        return autoInitialState;
    }

    public transitionRendering getTransitionsRenderedAs() {
        return transitionsRenderedAs;
    }

    public void setTransitionsRenderedAs(transitionRendering transitionsRenderedAs) {
        this.transitionsRenderedAs = transitionsRenderedAs;
    }

    protected static Element createElement(Document document, String tagname, Map<?, ?> attributes, String text) {
        // Create the new element.
        Element element = document.createElement(tagname);

        // Add the text element.
        if (text != null)
            element.appendChild(document.createTextNode(text));
        return element;
    }

    private static void savePreferencesHelper(String tagname, Object text, Document doc) {
        String strText = String.valueOf(text);
        Element element = createElement(doc, tagname, null, strText);
        doc.getDocumentElement().appendChild(element);
    }

	/**
	 * Saves the preferences stored in this profile in jflapPreferences.xml.
	 */
	public void savePreferences() {

		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
            File file;
            if (pathToFile != null) {
                file = pathToFile.toFile();
            } else {
                file = new File("");
            }
			
			builder = factory.newDocumentBuilder();
			Document doc = builder.newDocument();
			doc.appendChild(doc.createComment(Globals.FILE_CREATED_WITH_STRING));
			// Create and add the <structure> element.
			Element structureElement = createElement(doc, STRUCTURE_NAME, null,
					null);
			doc.appendChild(structureElement);

            String empty = "";
            if(emptyString.equals(lambda)) {
                empty = lambdaText;
            }
            else if(emptyString.equals(epsilon)) {
                empty = epsilonText;
            }

            savePreferencesHelper(EMPTY_STRING_NAME, empty, doc);
            savePreferencesHelper(TURING_FINAL_NAME, turingTransFromFinal, doc);
            savePreferencesHelper(UNDO_AMOUNT_NAME, undo_num, doc);
            savePreferencesHelper(AUTO_INITIAL_STATE, autoInitialState, doc);
            savePreferencesHelper(ACCEPT_FINAL_STATE, turingAcceptByFinalState, doc);
            savePreferencesHelper(ACCEPT_HALT, turingAcceptByHalting, doc);
            savePreferencesHelper(ALLOW_STAY, turingAllowStay, doc);
            savePreferencesHelper(LEGACY_ICONS, legacyUseLegacyIcons, doc);
            savePreferencesHelper(LEGACY_SUBMISSION_GUI, legacyUseLegacySubmissionGui, doc);

			DOMPrettier.makePretty(doc);
			Source s = new DOMSource(doc);
			Result r = new StreamResult(file);
			Transformer t;
			try {
				t = TransformerFactory.newInstance().newTransformer();
				try {
					t.transform(s, r);
				} catch (TransformerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} catch (TransformerConfigurationException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (TransformerFactoryConfigurationError e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

    private static Node preferencesElementLoaderHelper(String elementTag, Document doc) {
        return doc.getDocumentElement().getElementsByTagName(elementTag).item(0);
    }

    private void loadPreferenceElement(String elementTag, Document doc) {
        Node parent = preferencesElementLoaderHelper(elementTag, doc);
        if (parent!=null) {
            switch (elementTag) {
                case EMPTY_STRING_NAME:

            }
        }
    }

    /**
     * This method loads from the preferences file, if one exists.
     */
    public void loadPreferences() {
        pathToFile = getPreferencesFilePath();
        File file = pathToFile.toFile();

        if(file.exists()){
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            try {
                builder = factory.newDocumentBuilder(); Document doc;
                try {
                    doc = builder.parse(file);
                    Node parent;

                    //Set the empty string constant
                    parent = preferencesElementLoaderHelper(EMPTY_STRING_NAME, doc);
                    if (parent!=null) {
                        String empty = parent.getTextContent();
                        if(empty.equals(lambdaText))
                            setEmptyString(lambda);
                        else if(empty.equals(epsilonText))
                            setEmptyString(epsilon);
                    }

                    //Then set the Turing final state constant
                    parent = preferencesElementLoaderHelper(TURING_FINAL_NAME, doc);
                    if (parent!=null) {
                        String turingFinal = parent.getTextContent();
                        setTransitionsFromTuringFinalStateAllowed(turingFinal.equals("true"));
                    }

                    //set the Turing Acceptance ways.
                    parent = preferencesElementLoaderHelper(ACCEPT_FINAL_STATE, doc);
                    if (parent!=null) {
                        String acceptFinal = parent.getTextContent();
                        setAcceptByFinalState(acceptFinal.equals("true"));
                    }

                    parent = preferencesElementLoaderHelper(ACCEPT_HALT, doc);
                    if (parent!=null) {
                        String acceptHalt = parent.getTextContent();
                        setAcceptByHalting(acceptHalt.equals("true"));

                    }

                    //set the AllowStay option
                    parent = preferencesElementLoaderHelper(ALLOW_STAY, doc);
                    if (parent!=null) {
                        String allowStay = parent.getTextContent();
                        setAllowStay(allowStay.equals("true"));
                    }

                    //set the UseLegacyIcons option
                    parent = preferencesElementLoaderHelper(LEGACY_ICONS, doc);
                    if (parent!=null) {
                        boolean UseLegacyIcons = parent.getTextContent().equals("true");
                        setUseLegacyIcons(UseLegacyIcons);
                        IconKeeper.useNewIcons = !UseLegacyIcons;
                    }

                    //set the UseLegacySubmissionGui option
                    parent = preferencesElementLoaderHelper(LEGACY_SUBMISSION_GUI, doc);
                    if (parent!=null) {
                        setUseLegacySubmissionGui(parent.getTextContent().equals("true"));
                    }

                    //Now set the Undo amount
                    parent = preferencesElementLoaderHelper(UNDO_AMOUNT_NAME, doc);
                    if (parent!=null) {
                        String number = parent.getTextContent();
                        setNumUndo(Integer.parseInt(number));
                    }

                    //Now set Auto Initial State
                    parent = preferencesElementLoaderHelper(AUTO_INITIAL_STATE, doc);
                    if (parent!=null) {
                        boolean AutoInitialState = parent.getTextContent().equals("true");
                        setAutoInitialState(AutoInitialState);
                    }

                } catch (SAXException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } catch (ParserConfigurationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }
}
