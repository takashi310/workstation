package org.janelia.it.workstation.gui.browser.api.lifecycle;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.janelia.it.workstation.gui.util.WindowLocator;
import org.janelia.it.workstation.lifecycle.InterceptingEventQueue;
import org.janelia.it.workstation.lifecycle.LoggingEventListener;
import org.janelia.it.workstation.lifecycle.MessageSource;
import org.janelia.it.workstation.lifecycle.ReportRunner;
import org.janelia.it.workstation.shared.util.ConsoleProperties;
import org.openide.windows.OnShowing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This carries out tasks that must be done at startup, but may only be done 
 * when the application is ready to show.
 * 
 * @author fosterl
 */
@OnShowing
public class ShowingHook implements Runnable {
    public void run() {
        
        Logger log = LoggerFactory.getLogger(ShowingHook.class);
        
        JFrame frame = WindowLocator.getMainFrame();
        String title = ConsoleProperties.getString("console.Title") + " " + ConsoleProperties.getString("console.versionNumber");
        frame.setTitle(title);
        SessionMgr.getBrowser().supportMenuProcessing();
        
        // Log events.
        final InterceptingEventQueue interceptingEventQueue = new InterceptingEventQueue();
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(
                interceptingEventQueue);
        final LoggingEventListener loggingEventListener = new LoggingEventListener();
        Toolkit.getDefaultToolkit().addAWTEventListener(
                loggingEventListener, AWTEvent.MOUSE_EVENT_MASK);
        
        List<String> discriminators = new ArrayList<>();
        List<MessageSource> sources = new ArrayList<>();
        sources.add(interceptingEventQueue);
        discriminators.add(ReportRunner.MOUSE_EVENT_DISCRIMINATOR);
        //sources.add(loggingEventListener);
        //discriminators.add(ReportRunner.BUTTON_EVENT_DISCRIMINATOR);
        ReportRunner rptRunner = new ReportRunner(sources, discriminators);

        log.info("Showing main window");
        frame.setVisible(true);
        
        if (frame.getExtendedState()==JFrame.MAXIMIZED_BOTH) {
            // Workaround for a framework bug. Ensure the window doesn't cover the Windows toolbar. 
            log.info("Window is maximized. Resizing to make sure it doesn't cover Windows toolbar.");
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            frame.setSize(env.getMaximumWindowBounds().getSize());
            frame.setMaximizedBounds(env.getMaximumWindowBounds());
            frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
        else {
            Dimension currSize = frame.getSize();
            log.info("Not maximized. currSize="+currSize);
            if (currSize.width<20 || currSize.height<20) {
                log.info("Window is too small. Resetting to 80% of screen size.");
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                double width = screenSize.getWidth();
                double height = screenSize.getHeight();
                frame.setLocation(new Point(0, 30)); // 30 pixels down to avoid Mac toolbar at the top of the screen
                frame.setSize(new Dimension((int)Math.round(width*0.8), (int)Math.round(height*0.8)));
            }
        }
    }
}
