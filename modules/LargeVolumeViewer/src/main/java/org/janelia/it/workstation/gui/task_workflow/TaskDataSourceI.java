/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.janelia.it.workstation.gui.task_workflow;

import org.janelia.it.workstation.gui.large_volume_viewer.annotation.AnnotationModel;

public interface TaskDataSourceI {
    AnnotationModel getAnnotationModel();
}