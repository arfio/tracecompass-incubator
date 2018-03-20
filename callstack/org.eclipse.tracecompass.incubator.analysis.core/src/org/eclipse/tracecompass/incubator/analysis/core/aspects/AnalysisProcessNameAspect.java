/*******************************************************************************
 * Copyright (c) 2018 Ecole Polytechnique de Montreal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.aspects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxPidAspect;
import org.eclipse.tracecompass.incubator.analysis.core.aspects.ProcessNameAspect;
import org.eclipse.tracecompass.incubator.analysis.core.model.IHostModel;
import org.eclipse.tracecompass.incubator.analysis.core.model.ModelManager;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * Event aspect to resolve the process name of an event. It finds the model for
 * the host the aspect is from and returns the process name at the time of the
 * event.
 *
 * @author Guillaume Champagne
 * @author Alexis-Maurer Fortin
 * @author Hugo Genesse
 * @author Pierre-Yves Lajoie
 * @author Eva Terriault
 */
public final class AnalysisProcessNameAspect extends ProcessNameAspect {

    private static final AnalysisProcessNameAspect INSTANCE = new AnalysisProcessNameAspect();

    private AnalysisProcessNameAspect() {
        // Nothing to do
    }

    /**
     * Get the instance of this aspect
     *
     * @return The instance of this aspect
     */
    public static AnalysisProcessNameAspect getInstance() {
        return INSTANCE;
    }

    @Override
    public @Nullable String resolve(@NonNull ITmfEvent event) {
        Object pidObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), LinuxPidAspect.class, event);
        if (pidObj == null) {
            return null;
        }

        String hostId = event.getTrace().getHostId();
        IHostModel model = ModelManager.getModelFor(hostId);
        Integer pid = (Integer) pidObj;
        ITmfTimestamp timestamp = event.getTimestamp();
        String pname = model.getExecName(pid, timestamp.getValue());
        return pname;
    }
}
