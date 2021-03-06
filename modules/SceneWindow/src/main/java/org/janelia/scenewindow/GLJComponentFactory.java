
package org.janelia.scenewindow;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.awt.GLJPanel;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.openide.util.ImageUtilities;

/**
 *
 * @author brunsc
 */
public class GLJComponentFactory 
{
    // Creates a GL drawing surface in an OS appropriate manner
    // Creates a GLCanvas based display on Windows, where we might want
    // hardware stereo 3D to work.
    // Creates a GLJPanel based display on Mac, where GLCanvas behavior
    // is horrible.
    public static GLJComponent createGLJComponent(GLCapabilities capabilities) {
        if (false) {
            return new GLJPanelComponent(capabilities);
        }
        else {
            if (System.getProperty("os.name").startsWith("Mac"))
                return new GLJPanelComponent(capabilities);
            else 
                return new GLCanvasInJPanelComponent(capabilities);
        }
    }

    public static class GLCanvasInJPanelComponent extends JPanel
    implements GLJComponent 
    {
        private final Dimension tinySize = new Dimension(0, 0);
        private GLCanvas glCanvas;
        private JPanel playPanel;
        private JButton playReverseButton;
        private JButton playForwardButton;
        private JButton playPauseButton;

        public GLCanvasInJPanelComponent(GLCapabilities capabilities) {
            super(new BorderLayout());
            glCanvas = new GLCanvas(capabilities);
            add(glCanvas, BorderLayout.CENTER);
        
            buildControlsPanel();
           
            // This workaround avoids horrible GLCanvas resize behavior...
            glCanvas.addGLEventListener(new GLEventListener() {
                @Override
                public void init(GLAutoDrawable glad) {}
                @Override
                public void dispose(GLAutoDrawable glad) {}
                @Override
                public void display(GLAutoDrawable glad) {}
                @Override
                public void reshape(GLAutoDrawable glad, int i, int i1, int i2, int i3) {
                    // Avoid horrible GLCanvas resize behavior in Swing containers
                    // Thank you World Wind developers
                    glCanvas.setMinimumSize(tinySize);
                }
            });
        }
        
        private void buildControlsPanel() {
            playPanel = new JPanel();
            BoxLayout boxLayout = new BoxLayout(playPanel, BoxLayout.X_AXIS);
            playPanel.setLayout(boxLayout);
            playPanel.setAlignmentX(CENTER_ALIGNMENT);
            
            playReverseButton = new JButton();
            playReverseButton.setIcon(new ImageIcon(ImageUtilities.loadImage("org/janelia/horta/images/control_rewind_blue.png")));             
            playPanel.add(playReverseButton);
               
            playPauseButton = new JButton();
            playPauseButton.setIcon(new ImageIcon(ImageUtilities.loadImage("org/janelia/horta/images/control_pause_blue.png")));
            playPanel.add(playPauseButton);
            
            playForwardButton = new JButton();
            playForwardButton.setIcon(new ImageIcon(ImageUtilities.loadImage("org/janelia/horta/images/control_fastforward_blue.png")));
            playPanel.add(playForwardButton);

            add(playPanel, BorderLayout.SOUTH);
            playPanel.setVisible(false);
        }

        @Override
        public Component getInnerComponent() {
            return glCanvas;
        }

        @Override
        public JComponent getOuterComponent() {
            return this;
        }

        @Override
        public void setControlsVisibility (boolean visible) {
            playPanel.setVisible(visible);
        }
        
        @Override
        public void addPauseListener (ActionListener listener) {
            playPauseButton.addActionListener(listener);
        }
        
        @Override
        public void addPlayForwardListener (ActionListener listener) {
            playForwardButton.addActionListener(listener);
        }
        
        @Override        
        public void addPlayReverseListener (ActionListener listener) {
            playReverseButton.addActionListener(listener);
        }
        
        @Override
        public GLAutoDrawable getGLAutoDrawable() {
            return glCanvas;
        }

        // For some reason, the GLCanvas seems to eat the mouse events before they
        // could reach the enclosing JPanel
        @Override
        public void addMouseMotionListener(MouseMotionListener mml) {
            glCanvas.addMouseMotionListener(mml);
        }
        @Override
        public void addMouseListener(MouseListener ml) {
            glCanvas.addMouseListener(ml);
        }
        @Override
        public void addMouseWheelListener(MouseWheelListener mwl) {
            glCanvas.addMouseWheelListener(mwl);
        }

    }

    
    /**
    * Thin wrapper around GLJPanel, to support common interface between 
    * GLJPanel and GLCanvas based displays.
    * 
    * @author brunsc
    */
   public static class GLJPanelComponent extends GLJPanel
   implements GLJComponent 
   {
       public GLJPanelComponent(GLCapabilities capabilities) {
           super(capabilities);
           // setSkipGLOrientationVerticalFlip(true); // Maybe GL_FRAMEBUFFER_SRGB could work now NOPE
       }

       @Override
       public Component getInnerComponent() {
           return this;
       }

       @Override
       public JComponent getOuterComponent() {
           return this;
       }

       @Override
       public GLAutoDrawable getGLAutoDrawable() {
           return this;
       }

        @Override
        public void setControlsVisibility(boolean visible) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void addPlayForwardListener(ActionListener listener) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void addPlayReverseListener(ActionListener listener) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void addPauseListener(ActionListener listener) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

   }

}
