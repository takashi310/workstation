/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.janelia.it.workstation.gui.large_volume_viewer.controller;

import java.util.List;

import org.janelia.it.jacs.model.user_data.tiledMicroscope.TmNeuron;
import org.janelia.it.jacs.model.user_data.tiledMicroscope.TmWorkspace;
import org.janelia.it.workstation.gui.large_volume_viewer.style.NeuronStyle;

/**
 * Implement this to hear about workspace/all-annotation-scoped changes.
 * 
 * @author fosterl
 */
public interface GlobalAnnotationListener {
    void workspaceLoaded(TmWorkspace workspace);
    void neuronSelected(TmNeuron neuron);
    void neuronStyleChanged(TmNeuron neuron, NeuronStyle style);
    void neuronStylesChanged(List<TmNeuron> neuronList, NeuronStyle style);
    void neuronTagsChanged(List<TmNeuron> neuronList);
}
