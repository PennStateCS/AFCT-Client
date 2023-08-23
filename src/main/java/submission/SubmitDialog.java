package submission;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import java.net.URL;
import java.net.MalformedURLException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import gui.environment.Environment;
import file.XMLCodec;

public class SubmitDialog extends JDialog implements ActionListener {
    private Environment env;
    private JPanel submissionCards;
    private JTextField serverTF;
    private JTextField usernameTF;
    private JPasswordField passwordTF;
    private JComboBox homeworkCB;
    private JComboBox probCB;
    private JLabel descLabel;
    private JTextArea resultText;
    private JButton submit;

    // Preferences
    private String PREF_SERVER = "server";
    private String PREF_USERNAME = "username";
    private String PREF_PASSWORD = "password";
    private String PREF_HOMEWORK = "homework";
    private String PREF_PROBLEM = "problem";

    public SubmitDialog(Environment env) {
        Container c = this.getContentPane();
        JPanel submissionFrame = new JPanel();
        JPanel submittedFrame = new JPanel();
        GridBagConstraints leftCons = new GridBagConstraints();
        GridBagConstraints rightCons = new GridBagConstraints();

        this.env = env;
        this.submissionCards = new JPanel();
        this.serverTF = new JTextField();
        this.usernameTF = new JTextField();
        this.passwordTF = new JPasswordField();
        this.homeworkCB = new JComboBox();
        this.probCB = new JComboBox();
        this.descLabel = new JLabel();
        this.resultText = new JTextArea();
        this.submit = new JButton("Submit");

        this.submit.addActionListener(this);

        this.resultText.setEnabled(false);
        this.resultText.setLineWrap(true);
        this.resultText.setWrapStyleWord(true);

        this.setMinimumSize(new Dimension(300, 0));

        this.serverTF.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    fetchHomework(serverTF.getText());
                }
            }
        });

        probCB.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    Problem prob = (Problem)e.getItem();

                    descLabel.setText(prob.getDescription());
                }
                else {
                    descLabel.setText("");
                }
            }
        });

        homeworkCB.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    Homework homework = (Homework)e.getItem();

                    probCB.removeAllItems();

                    for (Problem p : homework.getProblems()) {
                        probCB.addItem(p);
                    }
                }
            }
        });

        leftCons.gridx = 0;
        leftCons.fill = GridBagConstraints.HORIZONTAL;
        leftCons.weightx = 0;
        rightCons.gridx = 1;
        rightCons.fill = GridBagConstraints.HORIZONTAL;
        rightCons.weightx = 1;
        submissionFrame.setLayout(new GridBagLayout());
        leftCons.gridy = 0;
        submissionFrame.add(new JLabel("Server:", SwingConstants.RIGHT), leftCons);
        rightCons.gridy = 0;
        submissionFrame.add(this.serverTF, rightCons);
        leftCons.gridy = 1;
        submissionFrame.add(new JLabel("Username:", SwingConstants.RIGHT), leftCons);
        rightCons.gridy = 1;
        submissionFrame.add(this.usernameTF, rightCons);
        leftCons.gridy = 2;
        submissionFrame.add(new JLabel("Password:", SwingConstants.RIGHT), leftCons);
        rightCons.gridy = 2;
        submissionFrame.add(this.passwordTF, rightCons);
        leftCons.gridy = 3;
        submissionFrame.add(new JLabel("Homework:", SwingConstants.RIGHT), leftCons);
        rightCons.gridy = 3;
        submissionFrame.add(this.homeworkCB, rightCons);
        leftCons.gridy = 4;
        submissionFrame.add(new JLabel("Problem:", SwingConstants.RIGHT), leftCons);
        rightCons.gridy = 4;
        submissionFrame.add(this.probCB, rightCons);
        leftCons.gridy = 5;
        submissionFrame.add(new JLabel("Description:", SwingConstants.RIGHT), leftCons);
        rightCons.gridy = 5;
        submissionFrame.add(this.descLabel, rightCons);

        submittedFrame.setLayout(new BorderLayout(5, 5));
        submittedFrame.add(this.resultText, BorderLayout.CENTER);

        this.submissionCards.setLayout(new CardLayout());
        this.submissionCards.add(submissionFrame, "submitting");
        this.submissionCards.add(submittedFrame, "submitted");

        c.add(submissionCards, BorderLayout.CENTER);
        c.add(submit, BorderLayout.SOUTH);

        this.pack();
        this.setInitialUIValues();
    }

    public void actionPerformed(ActionEvent e) {
        this.submit.setEnabled(false);

        try {
            File f = File.createTempFile("jflap", ".jff");
            XMLCodec x = new XMLCodec();
            long hwid = ((Homework)this.homeworkCB.getSelectedItem()).getId();
            long pid = ((Problem)this.probCB.getSelectedItem()).getId();
            MultipartEntityBuilder contentBuilder = MultipartEntityBuilder.create();
            HttpPost httppost = new HttpPost(StringUtils.stripEnd(this.serverTF.getText(), "/") + "/api/homework/submit");
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(this.usernameTF.getText(), this.passwordTF.getText());
            BasicCredentialsProvider credprov = new BasicCredentialsProvider();
            HttpClientBuilder builder = HttpClientBuilder.create();
            Preferences prefs = Preferences.userNodeForPackage(submission.SubmitDialog.class);

            prefs.put(PREF_SERVER, this.serverTF.getText());
            prefs.put(PREF_USERNAME, this.usernameTF.getText());
            prefs.put(PREF_PASSWORD, this.passwordTF.getText());
            prefs.putLong(this.serverTF.getText() + "_" + PREF_HOMEWORK, hwid);
            prefs.putLong(this.serverTF.getText() + "_" + hwid + "_" + PREF_PROBLEM, pid);
            x.encode(this.env.getObject(), f, null);
            contentBuilder.addTextBody("hwid", Long.toString(hwid));
            contentBuilder.addTextBody("pid", Long.toString(pid));
            contentBuilder.addBinaryBody("submission", f);
            httppost.setEntity(contentBuilder.build());
            credprov.setCredentials(AuthScope.ANY, creds);
            builder.setDefaultCredentialsProvider(credprov);

            HttpClient client = builder.build();
            HttpResponse res = client.execute(httppost);

            if (res.getStatusLine().getStatusCode() == 200) {
                CardLayout cl = (CardLayout)this.submissionCards.getLayout();
                JSONParser parser = new JSONParser();
                String jsonText = IOUtils.toString(res.getEntity().getContent());

                try {
                    Map submission = (Map)parser.parse(jsonText);

                    if ((boolean)submission.get("correct")) {
                        this.resultText.setText("Correct!");
                    }
                    else {
                        this.resultText.setText("Incorrect: " + (String)submission.get("feedback"));
                    }

                    this.resultText.setEnabled(true);
                } catch (ParseException pe) {
                    JOptionPane.showMessageDialog(null, "Could not parse json");
                }
                cl.next(this.submissionCards);
            } else if (res.getStatusLine().getStatusCode() == 401) {
                JOptionPane.showMessageDialog(null, "Login error. Check your username and password.");
            } else if (res.getStatusLine().getStatusCode() == 404) {
                JOptionPane.showMessageDialog(null, "The homework was not found. Perhaps you are past the deadline?");
            } else {
                JOptionPane.showMessageDialog(null, "Unknown error: " + IOUtils.toString(res.getEntity().getContent()));
            }

            if (res.getStatusLine().getStatusCode() != 200) {
                this.submit.setEnabled(false);
            }

            f.delete();
        } catch (UnsupportedEncodingException ue) {
            JOptionPane.showMessageDialog(null, "Unsupported encoding");
        } catch (IOException ie) {
            JOptionPane.showMessageDialog(null, "IO Exception");
        }
    }

    private void fetchHomework(String server) {
        String url = StringUtils.stripEnd(server, "/") + "/api/homework";

        try {
            InputStream is = new URL(url).openStream();
            JSONParser parser = new JSONParser();
            String jsonText = IOUtils.toString(is);

            try {
                JSONArray homework = (JSONArray)parser.parse(jsonText);

                this.homeworkCB.removeAllItems();

                for (Object h : homework) {
                    Map hmap = (Map)h;
                    ArrayList<Problem> problems = new ArrayList<Problem>();
                    long hid  = (Long)hmap.get("id");
                    String name = hmap.get("name").toString();
                    Homework hw = new Homework(hid, name);

                    for (Object p : (JSONArray)hmap.get("problems")) {
                        Map pmap = (Map)p;
                        long pid = (Long)pmap.get("id");
                        String pname = (String)pmap.get("name");
                        String pdesc = (String)pmap.get("description");
                        Problem prob = new Problem(pid, pname, pdesc);

                        problems.add(prob);
                    }

                    hw.setProblems(problems);
                    this.homeworkCB.addItem(hw);
                }
            } catch (ParseException pe) {
                JOptionPane.showMessageDialog(null, "Could not parse json");
            } finally {
                is.close();
            }
        } catch (MalformedURLException mue) {
            JOptionPane.showMessageDialog(null, "Malformed URL");
        } catch (IOException ie) {
            JOptionPane.showMessageDialog(null, "IO Exception");
        }
    }

    private void setInitialUIValues() {
        Preferences prefs = Preferences.userNodeForPackage(submission.SubmitDialog.class);
        String defaultServer = "";

        if (this.getClass().getResource("/SETTINGS/SERVER") != null) {
            try  {
                defaultServer = IOUtils.toString(this.getClass().getResourceAsStream("/SETTINGS/SERVER"));
            } catch (IOException e) {
            }
        }

        if (!prefs.get(PREF_SERVER, defaultServer).equals("")) {
            this.serverTF.setText(prefs.get(PREF_SERVER, defaultServer));
            this.serverTF.requestFocusInWindow();
            this.fetchHomework(prefs.get(PREF_SERVER, defaultServer));

            if (!prefs.get(prefs.get(PREF_SERVER, "") + "_" + PREF_HOMEWORK,"").equals("")) {
                long hwid = prefs.getInt(prefs.get(PREF_SERVER, "") + "_" + PREF_HOMEWORK, 0);
                ComboBoxModel hwModel = this.homeworkCB.getModel();

                for (int cidx = 0; cidx < hwModel.getSize(); cidx++) {
                    if (hwid == ((Homework)hwModel.getElementAt(cidx)).getId()) {
                        hwModel.setSelectedItem(hwModel.getElementAt(cidx));
                    }
                }

                if (!prefs.get(prefs.get(PREF_SERVER, "") + "_" + hwid + "_" + PREF_PROBLEM,"").equals("")) {
                    int probID = prefs.getInt(prefs.get(PREF_SERVER, "") + "_" + hwid + "_" + PREF_PROBLEM, 0);
                    ComboBoxModel probModel = this.probCB.getModel();

                    for (int pidx = 0; pidx < probModel.getSize(); pidx++) {
                        if (probID == ((Problem)probModel.getElementAt(pidx)).getId()) {
                            probModel.setSelectedItem(probModel.getElementAt(pidx));
                        }
                    }
                }
            }
        }

        this.usernameTF.setText(prefs.get(PREF_USERNAME, ""));
        this.passwordTF.setText(prefs.get(PREF_PASSWORD, ""));
    }
}
