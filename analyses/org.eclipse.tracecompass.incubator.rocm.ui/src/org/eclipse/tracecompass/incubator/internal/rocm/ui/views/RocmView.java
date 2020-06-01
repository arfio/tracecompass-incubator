/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.rocm.ui.views;

import com.google.common.collect.ImmutableList;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartDataProvider;
import org.eclipse.tracecompass.incubator.internal.callstack.ui.views.flamechart.Messages;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmXYDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.ui.views.timegraph.dataprovider.DataProviderBaseView;
import org.eclipse.tracecompass.internal.provisional.tmf.ui.widgets.timegraph.BaseDataProviderTimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.core.signal.TmfSelectionRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfWindowRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * @author Arnaud Fiorini
 *
 */
@SuppressWarnings("restriction")
public class RocmView extends DataProviderBaseView {

    private static final String[] COLUMN_NAMES = new String[] {
            Messages.CallStackView_FunctionColumn,
            Messages.CallStackView_DepthColumn,
            Messages.CallStackView_EntryTimeColumn,
            Messages.CallStackView_ExitTimeColumn,
            Messages.CallStackView_DurationColumn
    };

    private static final String[] FILTER_COLUMN_NAMES = new String[] {
            Messages.CallStackView_ThreadColumn
    };

    /** View ID. */
    public static final @NonNull String ID = "org.eclipse.tracecompass.analysis.rocm.ui.timegraph.view"; //$NON-NLS-1$

    /**
     * RocmView Constructor
     */
    public RocmView() {
        super(ID, new BaseDataProviderTimeGraphPresentationProvider(), ImmutableList.of(
            FlameChartDataProvider.ID + ":" + RocmCallStackAnalysis.ID, //$NON-NLS-1$
            RocmXYDataProvider.ID
        ));
        setTreeColumns(COLUMN_NAMES);
        setFilterColumns(FILTER_COLUMN_NAMES);
    }

    @Override
    protected void buildEntryList(@NonNull ITmfTrace trace, @NonNull ITmfTrace parentTrace, @NonNull IProgressMonitor monitor) {
        super.buildEntryList(trace, parentTrace, monitor);
    }

    @Override
    @TmfSignalHandler
    public void selectionRangeUpdated(final TmfSelectionRangeUpdatedSignal signal) {
        super.selectionRangeUpdated(signal);
    }

    @Override
    @TmfSignalHandler
    public void windowRangeUpdated(final TmfWindowRangeUpdatedSignal signal) {
        if (signal.getSource() == this) {
            return;
        }
        super.windowRangeUpdated(signal);
    }
}
