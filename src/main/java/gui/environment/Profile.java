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

import gui.editor.IconKeeper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import file.xml.DOMPrettier;
import gui.editor.TMTransitionCreator;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static gui.Globals.getPreferencesFilePath;

public class Profile {
    public static String LAMBDA = "\u03BB";     // Jinghui Lim added stuff
    public static String EPSILON = "\u03B5";    // see MultipleSimulateAction
	public String lambda = "\u03BB";
	public String epsilon = "\u03B5";
	public String lambdaText = "u03BB";
	public String epsilonText = "u03B5";
	private String emptyString = lambda;
	public String Color = "Original";
	public int undo_num = 50;
	
	/** The tag name for the empty string preference. */
	public String EMPTY_STRING_NAME = "empty_string";

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


	/**
	 * Determines whether transitions can be issued from the final
	 * state of a Turing machine.
	 * 
	 * @author Chris Morgan
	 */
	private boolean transTuringFinal;

    //default to acceptByFinalState, since that was how it used to be
    private boolean turingAcceptByFinalState; //I would rather have it a better way, but I'm short on Time - ~Henry
    private boolean turingAcceptByHalting; //I would rather have it a better way, but I'm short on Time - ~Henry

    private boolean turingAllowStay; //default to true since that was the old implementation

	/**
	 * A JCheckBoxMenuItem that displays and allows one to change transTuringFinal.
	 */
	private JCheckBoxMenuItem transTuringFinalCheckBox; 

	private JCheckBoxMenuItem turingAcceptByFinalStateCheckBox; 
	private JCheckBoxMenuItem turingAcceptByHaltingCheckBox; 
	private JCheckBoxMenuItem turingAllowStayCheckBox;


    /**
     * Legacy options
     */
    private boolean legacyUseLegacyIcons;
    private boolean legacyUseLegacySubmissionGui;
    private JCheckBoxMenuItem legacyUseLegacyIconsCheckBox;
    private JCheckBoxMenuItem legacyUseLegacySubmissionGuiCheckBox;


    public String pathToFile = "";
	
    public void setNumUndo(int nn){
    	undo_num = nn;
    }
	
	public Profile(){
        // Set default emptyString character to epsilon
		emptyString = epsilon;

		transTuringFinal = false;
		transTuringFinalCheckBox = new JCheckBoxMenuItem("Enable Transitions From Turing Machine Final States");
        transTuringFinalCheckBox.setSelected(transTuringFinal);
		transTuringFinalCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
            	setTransitionsFromTuringFinalStateAllowed(transTuringFinalCheckBox.isSelected());
            	savePreferences();
            }
        });

        turingAcceptByFinalState = true; //default to true, since that was the status before;
		turingAcceptByFinalStateCheckBox = new JCheckBoxMenuItem("Accept by Final State");
        turingAcceptByFinalStateCheckBox.setSelected(turingAcceptByFinalState);
		turingAcceptByFinalStateCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
            	setAcceptByFinalState(turingAcceptByFinalStateCheckBox.isSelected());
            	savePreferences();
            }
        });

        turingAcceptByHalting = false; //defaults to false, since it was not in previous JFLAP
        turingAcceptByHaltingCheckBox = new JCheckBoxMenuItem("Accept by Halting"); 
        turingAcceptByHaltingCheckBox.setSelected(turingAcceptByHalting); 
		turingAcceptByHaltingCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
            	setAcceptByHalting(turingAcceptByHaltingCheckBox.isSelected());
            	savePreferences();
            }
        });

        turingAllowStay = false; //defaults to false temporarily since that's how it was before
        turingAllowStayCheckBox = new JCheckBoxMenuItem("Allow stay for tape head on transition"); 
        turingAllowStayCheckBox.setSelected(turingAllowStay);
		turingAllowStayCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
            	setAllowStay(turingAllowStayCheckBox.isSelected());
            	savePreferences();
            }
        });


        legacyUseLegacyIcons = false; //defaults to false
        legacyUseLegacyIconsCheckBox = new JCheckBoxMenuItem("Use legacy toolbar icons");
        legacyUseLegacyIconsCheckBox.setSelected(legacyUseLegacyIcons);
        legacyUseLegacyIconsCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                IconKeeper.useNewIcons = !legacyUseLegacyIconsCheckBox.isSelected();
                setUseLegacyIcons(legacyUseLegacyIconsCheckBox.isSelected());
                savePreferences();
            }
        });

        legacyUseLegacySubmissionGui = true; //TODO: should defaults to false
        legacyUseLegacySubmissionGuiCheckBox = new JCheckBoxMenuItem("Use legacy submission interface");
        legacyUseLegacySubmissionGuiCheckBox.setSelected(legacyUseLegacySubmissionGui);
        legacyUseLegacySubmissionGuiCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                setUseLegacySubmissionGui(legacyUseLegacySubmissionGuiCheckBox.isSelected());
                savePreferences();
            }
        });
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
		transTuringFinal = t;
		transTuringFinalCheckBox.setSelected(t);
	}
	
	/**
	 * Sets whether Turing machines will accept by final state.
	 * 
	 * @param t yes or no
	 */
	public void setAcceptByFinalState(boolean t) {
		turingAcceptByFinalState = t;
		turingAcceptByFinalStateCheckBox.setSelected(t);
	}
	/**
	 * Sets whether Turing machines will accept by halting.
	 * 
	 * @param t yes or no
	 */
	public void setAcceptByHalting(boolean t) {
		turingAcceptByHalting = t;
		turingAcceptByHaltingCheckBox.setSelected(t);
	}

	/**
	 * Sets whether Turing machines will accept by halting.
	 * 
	 * @param t yes or no
	 */
	public void setAllowStay(boolean t) {
		turingAllowStay = t;
		turingAllowStayCheckBox.setSelected(t);
        TMTransitionCreator.setDirs(t);
	}

    public void setUseLegacyIcons(boolean t) {
        legacyUseLegacyIcons = t;
        legacyUseLegacyIconsCheckBox.setSelected(t);
    }

    public void setUseLegacySubmissionGui(boolean t) {
        legacyUseLegacySubmissionGui = t;
        legacyUseLegacySubmissionGuiCheckBox.setSelected(t);
    }

    public boolean getUseLegacySubmissionGui() {
        return legacyUseLegacySubmissionGui;
    }

	/**
	 * Returns whether transitions from Turing machine final states are allowed.
	 * 
	 * @return whether the transitions are allowed from final states
	 */
	public boolean transitionsFromTuringFinalStateAllowed() {
		return transTuringFinal;
	}
	
    public boolean getAcceptByFinalState(){
        return turingAcceptByFinalState;
    }

    public boolean getAcceptByHalting(){
        return turingAcceptByHalting;
    }

	/**
	 * Returns the JCheckBoxMenuItem that can allow the user to change whether
	 * Turing machine final states are allowed.
	 */
	public JCheckBoxMenuItem getTuringFinalCheckBox() {
		return transTuringFinalCheckBox;
	}

	public JCheckBoxMenuItem getAcceptByFinalStateCheckBox() {
		return turingAcceptByFinalStateCheckBox ;
    }
	public JCheckBoxMenuItem getAcceptByHaltingCheckBox() {
		return turingAcceptByHaltingCheckBox;
	}

	public JCheckBoxMenuItem getAllowStayCheckBox() {
		return turingAllowStayCheckBox;
	}

    public JCheckBoxMenuItem getUseLegacyIconsCheckBox() {
        return legacyUseLegacyIconsCheckBox;
    }

    public JCheckBoxMenuItem getUseLegacySubmissionGuiCheckBox() {
        return legacyUseLegacySubmissionGuiCheckBox;
    }

    protected static Element createElement(Document document, String tagname,
                                           Map<?, ?> attributes, String text) {
        // Create the new element.
        Element element = document.createElement(tagname);

        // Add the text element.
        if (text != null)
            element.appendChild(document.createTextNode(text));
        return element;
    }

	/**
	 * Saves the preferences stored in this profile in jflapPreferences.xml.
	 */
	public void savePreferences() {
		String empty = "";
		if(emptyString.equals(lambda)) empty = lambdaText;
	    else if(emptyString.equals(epsilon)) empty = epsilonText;
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
			File file = new File(pathToFile);
			
			builder = factory.newDocumentBuilder();
			Document doc = builder.newDocument();
			doc.appendChild(doc.createComment("Created with JFLAP "
					+ gui.AboutBox.VERSION + "."));
			// Create and add the <structure> element.
			Element structureElement = createElement(doc, STRUCTURE_NAME, null,
					null);
			doc.appendChild(structureElement);
			Element se = doc.getDocumentElement();		
			Element element = createElement(doc, EMPTY_STRING_NAME, null, ""+empty);
			se.appendChild(element);
			element = createElement(doc, TURING_FINAL_NAME, null, ""+transTuringFinal);
			se.appendChild(element);
			element = createElement(doc, UNDO_AMOUNT_NAME, null, ""+undo_num);
			se.appendChild(element);
			element = createElement(doc, ACCEPT_FINAL_STATE, null, ""+turingAcceptByFinalState);
			se.appendChild(element);
			element = createElement(doc, ACCEPT_HALT, null, ""+turingAcceptByHalting);
			se.appendChild(element);
			element = createElement(doc, ALLOW_STAY, null, "" + turingAllowStay);
			se.appendChild(element);

            element = createElement(doc, LEGACY_ICONS, null, "" + legacyUseLegacyIcons);
            se.appendChild(element);
            element = createElement(doc, LEGACY_SUBMISSION_GUI, null, "" + legacyUseLegacySubmissionGui);
            se.appendChild(element);
			
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

    /**
     * This method loads from the preferences file, if one exists.
     */
    public void loadPreferences() {
        String path = getPreferencesFilePath();
        pathToFile = path;

        if(new File(path).exists()){
            File file = new File(path);
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
