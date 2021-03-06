package org.janelia.workstation.browser.gui.listview.icongrid;

import java.awt.image.BufferedImage;
import java.util.List;

import org.janelia.workstation.core.model.Decorator;
import org.janelia.workstation.core.model.ImageModel;
import org.janelia.workstation.common.gui.support.Icons;
import org.janelia.workstation.common.gui.support.MouseForwarder;
import org.janelia.workstation.core.events.selection.SelectionModel;
import org.janelia.workstation.core.util.Utils;

/**
 * A button with a static icon and label.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class StaticImageButton<T,S> extends AnnotatedImageButton<T,S> {

    private static final BufferedImage MISSING_ICON = Icons.getImage("file_missing.png");

    // GUI
    private DecoratedImage infoPanel;
    private BufferedImage maxSizeImage;

    public StaticImageButton(T imageObject, ImageModel<T,S> imageModel, SelectionModel<T,S> selectionModel, String filepath) {
        super(imageObject, imageModel, selectionModel, filepath);
        List<Decorator> decorators = imageModel.getDecorators(imageObject);
        this.maxSizeImage = imageModel.getStaticIcon(imageObject);
        String errorText = null;
        if (maxSizeImage==null) {
            maxSizeImage = MISSING_ICON;
            errorText = "Selected result type not available";
        }
        this.infoPanel = new DecoratedImage(maxSizeImage, decorators, errorText);
        infoPanel.addMouseListener(new MouseForwarder(this, "DecoratedInfoPanel->StaticImageButton"));
        setMainComponent(infoPanel);
    }

    @Override
    public void setImageSize(int width, int height) {
        
        if (maxSizeImage!=null) {
            // Only scale icons down, never higher than their max resolution
            if (maxSizeImage.getWidth()>width) {
                infoPanel.setImage(Utils.getScaledImageByWidth(maxSizeImage, width));
            }   
        }

        super.setImageSize(width, height);
    }
}
