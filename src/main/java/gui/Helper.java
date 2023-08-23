package gui;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class Helper {

    public static ImageIcon getImageIcon(String path) {
        ImageIcon image = new ImageIcon(Main.testingPath + path);
        if (image.getImageLoadStatus() != MediaTracker.COMPLETE) {
            image = new ImageIcon(Objects.requireNonNull(Main.class.getResource(path)));
        }
        return image;
    }
}
