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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;

import javax.swing.JComponent;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JComponent;
import javax.swing.filechooser.FileFilter;

import gui.editor.EditorPane;
import gui.environment.AutomatonEnvironment;
import gui.environment.Environment;
import gui.viewer.AutomatonPane;
import gui.viewer.SelectionDrawer;
import gui.environment.Universe;




/**
  This utility was created to factor out the massive amounts of common code in the four Graphics saving action classes.
  */
public class SaveGraphUtility{

        public static void saveGraph(Component apane, JComponent c, String description,  String format){
           
           if (apane instanceof EditorPane){
               apane = ((EditorPane)apane).getAutomatonPane();
           }

           Image canvasimage = apane.createImage(apane.getWidth(),apane.getHeight());
           Graphics imgG = canvasimage.getGraphics();
           apane.paint(imgG);
           BufferedImage bimg = new BufferedImage(canvasimage.getWidth(null), canvasimage.getHeight(null), BufferedImage.TYPE_INT_RGB);
           Graphics2D g = bimg.createGraphics();
           g.drawImage(canvasimage, null, null);
           


           Universe.CHOOSER.resetChoosableFileFilters();
           Universe.CHOOSER.setAcceptAllFileFilterUsed(false);
        
           FileFilter spec = new FileNameExtensionFilter(description, format.split(","));

           Universe.CHOOSER.addChoosableFileFilter(spec);
           Universe.CHOOSER.addChoosableFileFilter(new AcceptAllFileFilter());
           Universe.CHOOSER.setFileFilter(spec);


           int result = Universe.CHOOSER.showSaveDialog(c);
           while (result == JFileChooser.APPROVE_OPTION) {
                    File file = Universe.CHOOSER.getSelectedFile();

                    if (!new FileNameExtensionFilter(description, format.split(",")).accept(file)) //only append if the chosen name is not acceptable
                        file = new File(file.getAbsolutePath() + "." + format.split(",")[0]);

                    if (file.exists()) {
                        int confirm = JOptionPane.showConfirmDialog(Universe.CHOOSER, "File exists. Shall I overwrite?", "FILE OVERWRITE ATTEMPTED", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.NO_OPTION){ 
                            result = Universe.CHOOSER.showSaveDialog(c);
                            continue;
                        }
                    }
                
             try  
             {
                    ImageIO.write(bimg, format.split(",")[0], file);
                    return;
              } 
             catch (IOException ioe)  
             {  
                    JOptionPane.showMessageDialog(c,
                    "Save failed with error:\n"+ioe.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
                    return;
             }
		}
        }

        public static void saveGraphUsingExistingFile(Component somePane, File file) {
            Component target = somePane;
            if (target instanceof EditorPane) {
                target = ((EditorPane) target).getAutomatonPane();
            }

            if (!(target instanceof JComponent)) {
                return;
            }

            JComponent component = (JComponent) target;
            component.setSize(component.getPreferredSize());
            component.validate();
            component.doLayout();
            component.repaint();

            if (component instanceof AutomatonPane) {
                ((AutomatonPane) component).requestTransform();
            }

            Dimension size = component.getPreferredSize();
            if (size.width <= 0 || size.height <= 0) {
                size = component.getSize();
            }
            if (size.width <= 0 || size.height <= 0) {
                size = new Dimension(900, 800);
            }

            BufferedImage tempImage = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height), BufferedImage.TYPE_INT_RGB);
            Graphics2D tempGraphics = tempImage.createGraphics();
            try {
                tempGraphics.setColor(Color.WHITE);
                tempGraphics.fillRect(0, 0, size.width, size.height);
                component.paint(tempGraphics);
            } finally {
                tempGraphics.dispose();
            }

            Dimension finalSize = component.getPreferredSize();
            if (finalSize.width <= 0 || finalSize.height <= 0) {
                finalSize = size;
            }

            // add some padding
            finalSize.width += 150;
            finalSize.height += 150;
            component.setSize(finalSize);

            Image canvasimage = component.createImage(finalSize.width, finalSize.height);
            Graphics2D imgG = (Graphics2D) canvasimage.getGraphics();
            try {
                imgG.setColor(Color.WHITE);
                imgG.fillRect(0, 0, finalSize.width, finalSize.height);
                component.paint(imgG);
            } finally {
                imgG.dispose();
            }

            BufferedImage bimg = new BufferedImage(finalSize.width, finalSize.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bimg.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, finalSize.width, finalSize.height);
                g.drawImage(canvasimage, 0, 0, null);
            } finally {
                g.dispose();
            }

            try {
                file = new File(file.getPath() + ".png");
                ImageIO.write(bimg, "png", file);
            } catch (IOException ioe) {
                JOptionPane.showMessageDialog(null,
                        "Save failed with error:\n" + ioe.getMessage(),
                        "Save failed", JOptionPane.ERROR_MESSAGE);
            }
        }

}
/**
  
* Java 6 has this, but not previous versions of java, so I'm writing it here.

* @author Henry
*/

class FileNameExtensionFilter extends FileFilter{
    String[] myAcceptedFormats;
    String myDescription;
    public FileNameExtensionFilter(String description, String... formats){
        myDescription = description;
        myAcceptedFormats = formats; 
    }

    public boolean accept(File f){
        if (f.isDirectory()) return true;
        for (int i = 0; i < myAcceptedFormats.length; i++)
            if (f.getName().endsWith("."+myAcceptedFormats[i])) return true;
        return false;
    }
    public String getDescription(){
        return myDescription;
    }
}

class AcceptAllFileFilter extends FileFilter
{
    public boolean accept(File f){
        return true;
    }
    public String getDescription(){
        return "All files";
    }
}

