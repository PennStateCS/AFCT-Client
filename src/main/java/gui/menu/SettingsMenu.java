package gui.menu;

import gui.action.ColorChooserAction;
import gui.action.EmptyStringCharacterAction;
import gui.action.SetUndoAmountAction;
import gui.editor.IconKeeper;
import gui.environment.Profile;
import gui.environment.Universe;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This class handles creation of the dropdown settings menu.
 *
 * @author Jesse Burdick-Pless
 */
public class SettingsMenu extends JMenu {
    private final EmptyStringCharacterAction emptyStringCharacterAction;
    private final SetUndoAmountAction setUndoAmountAction;
    private final ColorChooserAction colorChooserAction;

    private final TuringMachinePreferences turingMachinePreferences;
    private final LegacyOptions legacyOptions;
    private JCheckBoxMenuItem autoInitialStateCheckBox;

    public SettingsMenu() {
        super("Settings");

        this.emptyStringCharacterAction = new EmptyStringCharacterAction();
        this.setUndoAmountAction = new SetUndoAmountAction();
        this.colorChooserAction = new ColorChooserAction();

        this.turingMachinePreferences = new TuringMachinePreferences();
        this.legacyOptions = new LegacyOptions();
        setupAutoInitialStateCheckBox();


        this.add(emptyStringCharacterAction);
        this.add(setUndoAmountAction);
        this.add(autoInitialStateCheckBox);
        this.add(colorChooserAction);
        this.add(turingMachinePreferences);
        this.add(legacyOptions);

    }

    public SetUndoAmountAction getSetUndoAmountAction() {
        return this.setUndoAmountAction;
    }

    public TuringMachinePreferences getTuringMachinePreferences() {
        return this.turingMachinePreferences;
    }

    public TuringMachinePreferences getTMPrefs() {
        return this.turingMachinePreferences;
    }

    public LegacyOptions getLegacyOptions() {
        return this.legacyOptions;
    }

    public JCheckBoxMenuItem getAutoInitialStateCheckBox() {
        return this.autoInitialStateCheckBox;
    }


    /** CLASSES */

    public static class TuringMachinePreferences extends JMenu {
        public final JCheckBoxMenuItem turingTransitionsFromFinalStateCheckBox;
        public final JCheckBoxMenuItem turingAcceptByFinalStateCheckBox;
        public final JCheckBoxMenuItem turingAcceptByHaltingCheckBox;
        public final JCheckBoxMenuItem turingAllowStayCheckBox;

        public TuringMachinePreferences() {
            super("Turing Machine Preferences");

            turingTransitionsFromFinalStateCheckBox = new JCheckBoxMenuItem("Enable Transitions From Turing Machine Final States");
            turingTransitionsFromFinalStateCheckBox.setSelected(Universe.curProfile.transitionsFromTuringFinalStateAllowed());
            turingTransitionsFromFinalStateCheckBox.addActionListener(e -> {
                Universe.curProfile.setTransitionsFromTuringFinalStateAllowed(turingTransitionsFromFinalStateCheckBox.isSelected());
                Universe.curProfile.savePreferences();
            });
            this.add(turingTransitionsFromFinalStateCheckBox);

            turingAcceptByFinalStateCheckBox = new JCheckBoxMenuItem("Accept by Final State");
            turingAcceptByFinalStateCheckBox.setSelected(Universe.curProfile.getAcceptByFinalState());
            turingAcceptByFinalStateCheckBox.addActionListener(e -> {
                Universe.curProfile.setAcceptByFinalState(turingAcceptByFinalStateCheckBox.isSelected());
                Universe.curProfile.savePreferences();
            });
            this.add(turingAcceptByFinalStateCheckBox);


            turingAcceptByHaltingCheckBox = new JCheckBoxMenuItem("Accept by Halting");
            turingAcceptByHaltingCheckBox.setSelected(Universe.curProfile.getAcceptByHalting());
            turingAcceptByHaltingCheckBox.addActionListener(e -> {
                Universe.curProfile.setAcceptByHalting(turingAcceptByHaltingCheckBox.isSelected());
                Universe.curProfile.savePreferences();
            });
            this.add(turingAcceptByHaltingCheckBox);

            turingAllowStayCheckBox = new JCheckBoxMenuItem("Allow stay for tape head on transition");
            turingAllowStayCheckBox.setSelected(Universe.curProfile.getAllowStay());
            turingAllowStayCheckBox.addActionListener(e -> {
                Universe.curProfile.setAllowStay(turingAllowStayCheckBox.isSelected());
                Universe.curProfile.savePreferences();
            });
            this.add(turingAllowStayCheckBox);
        }
    }

    public static class LegacyOptions extends JMenu {
        public JCheckBoxMenuItem legacyUseLegacyIconsCheckBox;
        public JCheckBoxMenuItem legacyUseLegacySubmissionGuiCheckBox;

        public LegacyOptions() {
            super("Legacy Options");

            legacyUseLegacyIconsCheckBox = new JCheckBoxMenuItem("Use legacy toolbar icons");
            legacyUseLegacyIconsCheckBox.setSelected(Universe.curProfile.getLegacyUseLegacyIcons());
            legacyUseLegacyIconsCheckBox.addActionListener(e -> {
                IconKeeper.useNewIcons = !legacyUseLegacyIconsCheckBox.isSelected();
                Universe.curProfile.setUseLegacyIcons(legacyUseLegacyIconsCheckBox.isSelected());
                Universe.curProfile.savePreferences();
            });
            this.add(legacyUseLegacyIconsCheckBox);

            legacyUseLegacySubmissionGuiCheckBox = new JCheckBoxMenuItem("Use legacy submission interface");
            legacyUseLegacySubmissionGuiCheckBox.setSelected(Universe.curProfile.getUseLegacySubmissionGui());
            legacyUseLegacySubmissionGuiCheckBox.addActionListener(e -> {
                Universe.curProfile.setUseLegacySubmissionGui(legacyUseLegacySubmissionGuiCheckBox.isSelected());
                Universe.curProfile.savePreferences();
            });
            this.add(legacyUseLegacySubmissionGuiCheckBox);
        }
    }


    /** Setup Methods for Non-class Settings Objects */

    private void setupAutoInitialStateCheckBox() {
        //autoInitialStateCheckBox = new JCheckBoxMenuItem("Make 1st State the Initial State");
        autoInitialStateCheckBox = new JCheckBoxMenuItem("Auto Set the Initial State");
        autoInitialStateCheckBox.setSelected(Universe.curProfile.getAutoInitialState());
        autoInitialStateCheckBox.addActionListener(e -> {
            Universe.curProfile.setAutoInitialState(autoInitialStateCheckBox.isSelected());
            Universe.curProfile.savePreferences();
        });
    }

}
