package gui;

import file.Codec;
import file.DataException;
import file.ParseException;
import gui.action.OpenAction;
import gui.environment.Universe;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;

/**
 * DnDFileDropListener acts as an implementation of DropTargetListener that opens .jff files dropped onto the window.
 *
 * @author Jesse Burdick-Pless
 */
public class DnDFileDropListener extends DropTargetAdapter {
    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        // Optional: Provide visual feedback when drag enters
        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = dtde.getTransferable();

            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                Component source = ((DropTarget) dtde.getSource()).getComponent();
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                OpenAction.openFiles(files, source);
                dtde.dropComplete(true);
            } else {
                dtde.rejectDrop();
            }
        } catch (UnsupportedFlavorException | java.io.IOException e) {
            e.printStackTrace();
            dtde.rejectDrop();
        }
    }
}
