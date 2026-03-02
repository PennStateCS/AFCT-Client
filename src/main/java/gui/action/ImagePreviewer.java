package gui.action;

import gui.environment.Environment;
import gui.environment.Universe;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.*;

import static gui.Globals.getComponentSnapshot;
import static gui.Globals.guaranteedPositionFrameOnWindow;

public class ImagePreviewer extends JDialog {
    private JFrame frame;
    private Environment environment;
    private JFrame parentFrame;
    private JPanel imagePane;
    private JPanel buttonPane;
    private JLabel piclabel;
    private boolean closedNicely;
    private BufferedImage originalImg;
    private Component apane;
    private double heightRatio;

    public ImagePreviewer(Component apane, Environment environment, JFrame parentFrame) {
        // create a modal dialog that will stop the thread until closed
        super(parentFrame != null ? parentFrame : Universe.frameForEnvironment(environment), true);

        this.apane = apane;
        this.environment = environment;
        if (parentFrame != null){
            this.parentFrame = parentFrame;
        } else {
            if (this.environment != null) {
                this.parentFrame = Universe.frameForEnvironment(this.environment);
            }
        }

        // get image to preview
        this.originalImg = getComponentSnapshot(apane);

        // get dimensions
        heightRatio = (double) originalImg.getHeight() / (double) originalImg.getWidth();

        setUpGUI();
    }

    public ImagePreviewer(Component apane, JFrame parentFrame) {
        this(apane, null, parentFrame);
    }

    public ImagePreviewer(Component apane, Environment environment) {
        this(apane, environment, null);
    }

    private void setUpGUI() {
        String windowTitle = "Image preview";
        String parentTitle = "";
        if (environment != null) {
            File currentFile = environment.getFile();
            parentTitle = currentFile.getName();
        } else {
            if (parentFrame != null) {
                parentTitle = parentFrame.getTitle();
            }
        }
        if (!parentTitle.isEmpty()) {
            windowTitle = parentTitle + " - " + windowTitle;
        }
        this.setTitle(windowTitle);

        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.closedNicely = true;

        this.imagePane = new JPanel(new BorderLayout());
        this.buttonPane = new JPanel();
        JButton okButton = new JButton("Ok");
        JButton cancelButton = new JButton("cancel");

        ImagePreviewer frameToClose = this;
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                frameToClose.dispose();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frameToClose.dispose();
                frameToClose.closedNicely = false;
            }
        });

        this.addImage();
        this.buttonPane.add(okButton);
        this.buttonPane.add(cancelButton);

        this.getContentPane().setLayout(new BorderLayout());
        this.getContentPane().add(imagePane, BorderLayout.CENTER);
        this.getContentPane().add(buttonPane, BorderLayout.SOUTH);

        // resizes image when image is resized
        this.getContentPane().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeImagePanel();
            }
        });
    }

    // adds an image to the imagepreviewer
    private void addImage() {
        // resize to new dimensions
        int newWidth = 600;
        int newHeight = (int) (heightRatio * 600);
        BufferedImage scaled = resizeImageHQ(originalImg, newWidth, newHeight);

        // add to component
        this.piclabel = new JLabel(new ImageIcon(scaled));
//        piclabel.setBounds(piclabel.getBounds());
        this.imagePane.add(piclabel, BorderLayout.CENTER);
    }

    // resizes image to fit on the panel
    private void resizeImagePanel() {
        // get new image
        int newWidth = this.getContentPane().getWidth();
        if (newWidth <= 0) return;

        int newHeight = (int) (heightRatio * newWidth);
        //BufferedImage bimg = resizeImageHQ(originalImg, newWidth, newHeight);
        BufferedImage bimg = getComponentSnapshot(apane, newWidth, newHeight);

        // put on new image
        this.piclabel.setIcon(new ImageIcon(bimg));
        this.piclabel.revalidate();
    }

    // taken from https://www.baeldung.com/java-resize-image
    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics2D.dispose();
        return resizedImage;
    }

    private BufferedImage resizeImageHQ(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();

        g2.setComposite(AlphaComposite.Src);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();
        return resized;
    }


    // displays the imagepreviewer, wait for close
    // @return boolean whether the dialog closed nicely
    public Boolean display() {
        this.pack();
        setLocationRelativeTo(parentFrame);
        this.toFront();
        this.setVisible(true);
        return this.closedNicely;
    }
}
