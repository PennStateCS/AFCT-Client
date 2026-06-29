package gui.action;

import gui.Globals;
import gui.environment.Environment;
import gui.environment.Universe;
import submission.AFCTClient;
import submission.SubmitWindow;

import java.awt.event.ActionEvent;
import java.io.Serializable;

import static gui.Globals.positionFrameNearWindow;

public class SubmitAction extends RestrictedAction {

    private static final long serialVersionUID = 1L;

    private final Serializable obj;
    private final Environment environment;

    public SubmitAction(Serializable obj, Environment environment) {
        super("Submit", null);
        this.obj = obj;
        this.environment = environment;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        // 🔐 Authentication Gate
        AFCTClient client = Globals.sessionHandler.requireAuthenticated();

        if (client == null) {
            // Login cancelled or failed
            return;
        }

        // 🔍 Check if a SubmitWindow already exists for this environment
        SubmitWindow submitWindow =
                (SubmitWindow) Universe.submitDialogForEnvironment(environment);

        if (submitWindow == null) {

            submitWindow = new SubmitWindow(environment);

            Universe.registerSubmitDialog(environment, submitWindow);

            submitWindow.pack();

            positionFrameNearWindow(
                    submitWindow,
                    Globals.Position.RIGHT,
                    Universe.frameForEnvironment(environment)
            );
        }

        submitWindow.displaySubmitWindow();
    }
}
