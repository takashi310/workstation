/*
 * Licensed under the Janelia Farm Research Campus Software Copyright 1.1
 * 
 * Copyright (c) 2014, Howard Hughes Medical Institute, All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *     1. Redistributions of source code must retain the above copyright notice, 
 *        this list of conditions and the following disclaimer.
 *     2. Redistributions in binary form must reproduce the above copyright 
 *        notice, this list of conditions and the following disclaimer in the 
 *        documentation and/or other materials provided with the distribution.
 *     3. Neither the name of the Howard Hughes Medical Institute nor the names 
 *        of its contributors may be used to endorse or promote products derived 
 *        from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, ANY 
 * IMPLIED WARRANTIES OF MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * REASONABLE ROYALTIES; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package janelia.lvv.tileloader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.openide.LifecycleManager;
import org.openide.modules.ModuleInstall;
import org.openide.util.Exceptions;
import org.openide.windows.WindowManager;

public class Installer extends ModuleInstall
{

    /**
     * Run some tile loading benchmarks (TODO)
     * "restored()" is the "main()" of Netbeans platform applications.
     */
    @Override
    public void restored()
    {
        System.out.println("Running first test load...");
        List<Integer> sliceIndices = new ArrayList<Integer>();
        for (int i = 20; i >= 10; --i)
            sliceIndices.add(i);
        try {
            // 1 - Create a source of volume bricks
            URL testUrl = new URL("file:///F:/Users/cmbruns/mouseLightPerformance/rendered/tiff_stack_per_channel/tile_list.txt");
            BrickSetSource source = new BrickSetSource(testUrl);
            // 2 - Create a selector of slices
            LoadStrategem selector = new RandomSliceSelector(source, 10);
            // 3 - Create a loader, appropriate to the source format
            BrickSliceLoader loader = new ClackTiffSliceLoader(); // TODO - use a factory
            // 4 - measure the tile loading performance
            LoadTimeMeasurement measurement = new LoadTimeMeasurement(loader, selector);
            
            measurement.report(System.out);

        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
        }
        
        // Exit the application after running the tests
        WindowManager.getDefault().invokeWhenUIReady(new Runnable()
        {
            public void run()
            {
                // any code here will be run with the UI is available
                // Exit the application
                LifecycleManager.getDefault().exit(); 
            }
        });

    }

}
