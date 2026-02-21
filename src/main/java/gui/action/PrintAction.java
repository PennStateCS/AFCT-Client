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




package gui.action;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import gui.editor.EditorPane;
import gui.environment.Environment;

/**
 * This action handles printing. It will attempt to print the currently active
 * component in the environment.
 * 
 * @author Thomas Finley
 */

public class PrintAction extends RestrictedAction {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * Instantiates a new <CODE>PrintAction</CODE>.
	 * 
	 * @param environment
	 */
	public PrintAction(Environment environment) {
		super("Print", null);
		this.environment = environment;
		putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P,
				MAIN_MENU_MASK));
	}

	/**
	 * This will begin printing.
	 * 
	 * @param e
	 *            the action event
	 */
	public void actionPerformed(ActionEvent e) {
		JComponent c = (JComponent) environment.getActive();
		Component apane = environment.tabbed.getSelectedComponent();
		PrintUtilities.printComponent(environment, c, apane);
	}

	/** The environment. */
	private Environment environment;

	/**
	 * This is the work of Marty Hall from JHU in 1999. He made his source code
	 * "freely available for unrestricted use." It has been adapted to take
	 * advantage of some facilities of Swing.
	 */
	private static class PrintUtilities implements Printable {
        private Environment environment;
		public static void printComponent(Environment environment, JComponent c, Component apane) {
			new PrintUtilities(environment, c, apane).print();
		}

        public static void printComponent(JComponent c, Component apane) {
            new PrintUtilities(null, c, apane).print();
        }

		public PrintUtilities(Environment environment, JComponent componentToBePrinted, Component apane) {
			this.environment = environment;
            this.componentToBePrinted = componentToBePrinted;
			if (apane instanceof EditorPane){
				apane = ((EditorPane)apane).getAutomatonPane();
			}
			this.apane = apane;
		}

		public void print() {
			PrinterJob printJob = PrinterJob.getPrinterJob();
			if (printJob==null)
			{
				System.err.println("Error in Printing");
			}
			else
			{
				boolean accepted_preview = showPreview();
				if (!accepted_preview) {
					return;
				}
				if (componentToBePrinted instanceof PrintAction.Bounds) {
					PrintAction.Bounds b = (PrintAction.Bounds) componentToBePrinted;
					Rectangle2D bounds = b.printerBounds();
					Paper paper = new Paper();
					paper.setSize(2.0 * bounds.getX() + bounds.getWidth(), 2.0
							* bounds.getY() + bounds.getHeight());
					paper.setImageableArea(bounds.getX(), bounds.getY(), bounds
							.getWidth(), bounds.getHeight());
					PageFormat pf = new PageFormat();
					pf.setPaper(paper);
					printJob.setPrintable(this, pf);
				} else {
					printJob.setPrintable(this);
				}
				if (printJob.printDialog())
					try {
						printJob.print();
					} catch (PrinterException pe) {
						System.err.println("Error printing: " + pe);
					}
			}
		}

		// shows a preview of the image to print
		private boolean showPreview() {
			// get image to preview
			Image canvasimage = apane.createImage(apane.getWidth(),apane.getHeight());
			Graphics imgG = canvasimage.getGraphics();
			apane.paint(imgG);
			BufferedImage bimg = new BufferedImage(canvasimage.getWidth(null), canvasimage.getHeight(null), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = bimg.createGraphics();
			g.drawImage(canvasimage, null, null);

			// preview image
			ImagePreviewer imgpreview = new ImagePreviewer(bimg, environment);
			return imgpreview.display();
		}

		public int print(Graphics g, PageFormat pageFormat, int pageIndex) {
			if (pageIndex > 0) {
				return NO_SUCH_PAGE;
			} else {
				Graphics2D g2d = (Graphics2D) g;
				g2d.translate(pageFormat.getImageableX(), pageFormat
						.getImageableY());
				Rectangle2D clip = g2d.getClipBounds();
				Rectangle2D size = new Rectangle(apane.getBounds());
				double wratio = clip.getWidth() / size.getWidth();
				double hratio = clip.getWidth() / size.getWidth();
				if (wratio < hratio)
					g2d.scale(wratio, wratio);
				else
					g2d.scale(hratio, hratio);
				componentToBePrinted.print(g2d);
				return PAGE_EXISTS;
			}
		}

		private JComponent componentToBePrinted;
		private Component apane;
	}

	/**
	 * A component can implement this method if it wishes to indicate that it
	 * draws in a specified bound. This is used to determine a ratio for paper
	 * size if printer actions are intended to be written to a vector graphics
	 * file (as in OS X).
	 */
	public static interface Bounds {
		/** Returns the bounds. */
		public Rectangle2D printerBounds();
	}
}

