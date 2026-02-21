package gui.action;

import gui.environment.Environment;
import gui.environment.Universe;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.*;

import static gui.Globals.guaranteedPositionFrameOnWindow;

public class ImagePreviewer extends JDialog {
    private JFrame frame;
    private Environment environment;
    private JFrame parentFrame;
    private JPanel imagePane;
    private JPanel buttonPane;
    private JLabel piclabel;
    private boolean closedNicely;
    private BufferedImage img;

    public ImagePreviewer(BufferedImage bimg, Environment environment, JFrame parentFrame) {
        // create a modal dialog that will stop the thread until closed
        super(parentFrame != null ? parentFrame : Universe.frameForEnvironment(environment), true);

        this.environment = environment;
        if (parentFrame != null){
            this.parentFrame = parentFrame;
        } else {
            if (this.environment != null) {
                this.parentFrame = Universe.frameForEnvironment(this.environment);
            }
        }

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

        this.addImage(bimg);
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

    public ImagePreviewer(BufferedImage bimg, JFrame parentFrame) {
        this(bimg, null, parentFrame);
    }

    public ImagePreviewer(BufferedImage bimg, Environment environment) {
        this(bimg, environment, null);
    }

    // adds an image to the imagepreviewer
    private void addImage(BufferedImage bimg) {
        // get dimensions
        int imgwidth = bimg.getWidth();
        int imgheight = bimg.getHeight();
        double heightratio = (double) imgheight / (double) imgwidth;

        // resize to new dimensions
        int new_width = 600;
        int new_height = (int) (heightratio * 600);
        bimg = resizeImage(bimg, new_width, new_height);

        // add to component
        this.piclabel = new JLabel(new ImageIcon(bimg));
        piclabel.setBounds(piclabel.getBounds());
        
        this.imagePane.add(piclabel, BorderLayout.CENTER);
        this.img = bimg;
    }

    // resizes image to fit on the panel
    private void resizeImagePanel() {
        // get dimensions
        int imgwidth = this.img.getWidth();
        int imgheight = this.img.getHeight();
        double heightratio = (double) imgheight / (double) imgwidth;

        // get new image
        int new_width = this.getContentPane().getWidth();
        int new_height = (int) (heightratio * new_width);
        BufferedImage bimg = resizeImage(this.img, new_width, new_height);

        // put on new image
        this.piclabel.setIcon(new ImageIcon(bimg));
        this.piclabel.revalidate();
    }

    // taken from https://www.baeldung.com/java-resize-image
    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics2D.dispose();
    return resizedImage;
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
