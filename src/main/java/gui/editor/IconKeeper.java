package gui.editor;

import javax.swing.*;

public class IconKeeper {
    public static boolean useNewIcons = true;

    // Old icons
    private static final String ARROW_ICON = "/ICON/arrow.gif";
    private static final String STATE_ICON = "/ICON/state.gif";
    private static final String TRANSITION_ICON = "/ICON/transition.gif";
    private static final String DELETE_ICON = "/ICON/delete.gif";
    private static final String UNDO_ICON = "/ICON/undo2.jpg";
    private static final String REDO_ICON = "/ICON/redo.jpg";
    private static final String BUILDING_BLOCK_ICON = "/ICON/blocks.gif";
    private static final String BLOCK_TRANSITION_ICON = "/ICON/blockTransition.gif";

    // New icons
    private static final String STATE_ICON_NEW = "/ICON/JBP-Icons/state-wip.png";
    private static final String TRANSITION_ICON_NEW = "/ICON/JBP-Icons/transition-wip3.png";
    private static final String DELETE_ICON_NEW = "/ICON/JBP-Icons/delete-wip-smallx.png";
    private static final String UNDO_ICON_NEW = "/ICON/JBP-Icons/undo-wip2.png";
    private static final String REDO_ICON_NEW = "/ICON/JBP-Icons/redo-wip2.png";
    private static final String BLOCK_TRANSITION_ICON_NEW = "/ICON/JBP-Icons/block-transition-wip-dark.png";


    /**
     * Returns the tool icon from the given path.
     *
     * @param tool the tool object to get the icon for
     * @param iconPath the path to the tool icon in the resources directory
     * @return the tool icon
     */
    private static Icon getIcon(Object tool, String iconPath) {
        // TODO: look into replacing all icons with SVGs so they can be scaled for diff resolutions and still look good
        java.net.URL url = tool.getClass().getResource(iconPath);
        return new javax.swing.ImageIcon(url);
        //ImageIcon originalIcon = new ImageIcon(url);
        //Image scaledImage = originalIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
        //return new ImageIcon(scaledImage);
    }

    /**
     * Returns the correct tool icon.
     *
     * @param tool the tool object to get the icon for
     * @param iconPath the path to the tool icon in the resources directory
     * @param newIconPath the path to the new tool icon in the resources directory
     * @return the tool icon
     */
    private static Icon getToolIcon(Object tool, String iconPath, String newIconPath) {
        if (IconKeeper.useNewIcons) {
            return getIcon(tool, newIconPath);
        } else  {
            return getIcon(tool, iconPath);
        }
    }

    /**
     * Returns the arrow tool icon.
     *
     * @param tool the tool object to get the icon for
     * @return the arrow tool icon
     */
    public static Icon getArrowToolIcon(Object tool) {
        return getToolIcon(tool, ARROW_ICON, ARROW_ICON);
    }

    /**
     * Returns the state tool icon.
     *
     * @param tool the tool object to get the icon for
     * @return the state tool icon
     */
    public static Icon getStateToolIcon(Object tool) {
        return getToolIcon(tool, STATE_ICON, STATE_ICON_NEW);
    }

    /**
     * Returns the transition tool icon.
     *
     * @param tool the tool object to get the icon for
     * @return the transition tool icon
     */
    public static Icon getTransitionToolIcon(Object tool) {
        return getToolIcon(tool, TRANSITION_ICON, TRANSITION_ICON_NEW);
    }

    /**
     * Returns the delete tool icon.
     *
     * @param tool the tool object to get the icon for
     * @return the delete tool icon
     */
    public static Icon getDeleteToolIcon(Object tool) {
        return getToolIcon(tool, DELETE_ICON, DELETE_ICON_NEW);
    }

    /**
     * Returns the undo tool icon.
     *
     * @param tool the tool object to get the icon for
     * @return the undo tool icon
     */
    public static Icon getUndoToolIcon(Object tool) {
        return getToolIcon(tool, UNDO_ICON, UNDO_ICON_NEW);
    }

    /**
     * Returns the redo tool icon.
     *
     * @param tool the tool object to get the icon for
     * @return the redo tool icon
     */
    public static Icon getRedoToolIcon(Object tool) {
        return getToolIcon(tool, REDO_ICON, REDO_ICON_NEW);
    }

    /**
     * Returns the building block tool icon.
     *
     * @param tool the tool object to get the icon for
     * @return the building block tool icon
     */
    public static Icon getBuildingBlockToolIcon(Object tool) {
        return getToolIcon(tool, BUILDING_BLOCK_ICON, BUILDING_BLOCK_ICON);
    }

    /**
     * Returns the block transition tool icon.
     *
     * @param tool the tool object to get the icon for
     * @return the block transition tool icon
     */
    public static Icon getBlockTransitionToolIcon(Object tool) {
        return getToolIcon(tool, BLOCK_TRANSITION_ICON, BLOCK_TRANSITION_ICON_NEW);
    }
}
