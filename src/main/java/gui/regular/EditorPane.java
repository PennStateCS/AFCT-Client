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





package gui.regular;

import java.awt.*;
import java.awt.event.*;
import java.lang.ref.*;
import javax.swing.*;
import javax.swing.event.*;

import gui.TextFieldSizeSlider;
import regular.*;

/**
 * The editor pane for a regular expression allows the user to change the
 * regular expression.
 * 
 * @author Thomas Finley
 */

public class EditorPane extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new editor pane for a given regular expression.
	 * 
	 * @param expression
	 *            the regular expression
	 */
	public EditorPane(RegularExpression expression) {
		// super(new BorderLayout());
		this.expression = expression;
		field.setText(expression.asString());
		field.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				updateExpression();
			}
		});
		field.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				updateExpression();
			}

			public void removeUpdate(DocumentEvent e) {
				updateExpression();
			}

			public void changedUpdate(DocumentEvent e) {
				updateExpression();
			}
		});
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(new JLabel("Edit the regular expression below:"));
		
		source = new TextFieldSizeSlider(field, JSlider.HORIZONTAL, "Input Field Text Size");
		JLabel special_characters = new JLabel("<html><i>Special characters:{(, ), !, +, *}<i></html>"); // italics
		
		int maxTextHeight = ((TextFieldSizeSlider) source).getMaxSize() / 10 + 10;
		field.setPreferredSize(new Dimension(field.getPreferredSize().width, maxTextHeight / 2));
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxTextHeight));
		add(field);
		add(Box.createVerticalGlue());
		add(source);
		add(special_characters);

		this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeTextField();
            }
        }); 
	}

	/*
		Resize the text field when the window changes
		Matches the slider position
	*/
	private void resizeTextField() {
		int newHeight = source.getValue() / 10;
		int newWidth = this.getWidth();
		field.setSize(new Dimension(newWidth, newHeight + 10));
		field.setFont(new Font("Default", Font.PLAIN, newHeight));
	}

	/**
	 * This is called when the regular expression should be updated to accord
	 * with the field.
	 */
	private void updateExpression() {
		expression.change(ref);
	}

	/** The regular expression. */
	private RegularExpression expression;

	/** The field where the expression is displayed and edited. */
	private JTextField field = new JTextField("");

	private JSlider source;

	/**
	 * The expression change listener for a regular expression detects if there
	 * are changes in the environment, and if so, changes the display.
	 */
	private ExpressionChangeListener listener = new ExpressionChangeListener() {
		public void expressionChanged(ExpressionChangeEvent e) {
			field.setText(e.getExpression().asString());
		}
	};

	/** The reference object. */
	private Reference<String> ref = new WeakReference<String>(null) {
		public String get() {
			return field.getText();
		}
	};
}
