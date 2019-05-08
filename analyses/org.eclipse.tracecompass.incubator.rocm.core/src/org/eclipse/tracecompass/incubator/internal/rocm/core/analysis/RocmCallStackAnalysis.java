/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.tmf.core.analysis.requirements.TmfAbstractAnalysisRequirement;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
/**
 * ROCm call stack analysis
 *
 * @author Arnaud Fiorini
 *
 */
public class RocmCallStackAnalysis extends InstrumentedCallStackAnalysis {

    /**
     * Call stack analysis ID
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.rocm.core.analysis.callstack"; //$NON-NLS-1$

    @Override
    protected ITmfStateProvider createStateProvider() {
        return new RocmCallStackStateProvider(Objects.requireNonNull(getTrace()));
    }

    @Override
    public @NonNull Iterable<@NonNull TmfAbstractAnalysisRequirement> getAnalysisRequirements() {
        return Collections.emptyList();
    }

    @Override
    protected @NonNull Collection<@NonNull Integer> getEdgeQuarks() {
        ITmfStateSystem ss = getStateSystem();
        if (ss == null) {
            return Collections.emptyList();
        }
        int edgeQuark = ss.optQuarkAbsolute(RocmCallStackStateProvider.EDGES);
        if (edgeQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return Collections.emptyList();
        }
        return ss.getSubAttributes(edgeQuark, false);
    }
}
