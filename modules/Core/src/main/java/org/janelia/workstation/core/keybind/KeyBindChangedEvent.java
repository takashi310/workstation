package org.janelia.workstation.core.keybind;

import org.janelia.workstation.core.actions.Action;

/**
 * A key bind has changed and should be saved to the database.
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class KeyBindChangedEvent {
    
    private KeyboardShortcut shortcut;
    private Action existingAction;
    private Action newAction;
    
    public KeyBindChangedEvent(KeyboardShortcut shortcut, Action existingAction, Action newAction) {
        super();
        this.shortcut = shortcut;
        this.existingAction = existingAction;
        this.newAction = newAction;
    }

    public KeyboardShortcut getShortcut() {
        return shortcut;
    }

    public Action getExistingAction() {
        return existingAction;
    }

    public Action getNewAction() {
        return newAction;
    }
}
