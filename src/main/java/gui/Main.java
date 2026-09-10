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





package gui;
import file.Codec;
import file.ParseException;
import file.xml.Transducer;
import file.xml.TransducerFactory;
import gui.action.NewAction;
import gui.action.OpenAction;
import gui.editor.IconKeeper;
import gui.environment.Profile;
import gui.environment.Universe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javax.swing.UIManager;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static gui.Globals.*;

/**
 * This is the class that starts JFLAP.
 * 
 * @author Thomas Finley
 * @author Moti Ben-Ari
 * @modified by Kyung Min (Jason) Lee
 */

public class Main {
	
	private static boolean dontQuit;  // Don't quit when Quit selected
	
	public static boolean getDontQuit() {
		return dontQuit;
	}
	/**
	 * Starts JFLAP. This sets various system properties. If there are command
	 * line arguments, this will attempt to open them as JFLAP files. If there
	 * are no arguments, this will call on {@link gui.action.NewAction#showNew}
	 * to display a choice for a new structure.
	 * 
	 * @param args
	 *            the command line arguments, which may hold files to open
	 */
	public static void main(String[] args) {
		// Make sure we're not some old version.
		try {
			String v = System.getProperty("java.specification.version");
			double version = Double.parseDouble(v) + 0.00001;
			if (version < 1.5) {
				javax.swing.JOptionPane.showMessageDialog(null,
						"Java 1.5 or higher required to run JFLAP!\n"
						+ "You appear to be running Java " + v + ".\n"
						+ "This program will now exit.");
				System.exit(0);
			}
		} catch (SecurityException e) {
			// Eh, that shouldn't happen.
		}

		//TODO add the updater creation here maybe?
		// also add updater button to gui menu bar
		
		// Set the AWT exception handler. This may not work in future
		// Java versions.
		try {
			// This is a useless statement that forces the catcher to
			// compile.
			if (gui.ThrowableCatcher.class == null)
				;
			System.setProperty("sun.awt.exception.handler",
			"gui.ThrowableCatcher");
		} catch (SecurityException e) {
			System.err.println("Warning: could not set the "
					+ "AWT exception handler.");
		}
		
		// Apple is stupid.
		try {
			// Well, Apple WAS stupid...
			if (System.getProperty("os.name").startsWith("Mac OS")
					&& System.getProperty("java.specification.version").equals(
					"1.3"))
				System.setProperty("com.apple.hwaccel", "false");
		} catch (SecurityException e) {
			// Bleh.
		}
		// Sun is stupider.
		try {
			System.setProperty("java.util.prefs.syncInterval", "2000000");
		} catch (SecurityException e) {
			// Well, not key.
		}
		// Prompt the user for newness.
		NewAction.showNew();
		if (args.length > 0) {
			int start = 0;
			if(args[0].equals("update")){
				start = 2;
			}
			
			for (int i = start; i < args.length; i++) {
				Codec[] codecs = (Codec[]) Universe.CODEC_REGISTRY
				.getDecoders().toArray(new Codec[0]);
				try {
					OpenAction.openFile(new File(args[i]), codecs);
				} catch (ParseException e) {
					System.err.println("Could not open " + args[i] + ": "
							+ e.getMessage());
				}
			}
		}
        Universe.curProfile.loadPreferences();
		updater.updatePopup.showOnLoad();

		if (args.length >= 2) {
			print(Arrays.toString(args));
			if(args[0].equals("update")){
				File oldJar = new File(args[1]);
				File rename = new File(oldJar.getParent() + File.separator + "BACKUP-" + oldJar.getName());
				if (!oldJar.renameTo(rename)) {
					errorPrint("Unable to rename old application jar file.");
				}
			}
		}
	}
}
