/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.internal.tmf.core.model.xy.AbstractTreeCommonXDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.model.YModel;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.model.xy.IYModel;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.TreeMultimap;

/**
 * @author Arnaud Fiorini
 *
 */
@SuppressWarnings("restriction")
public class RocmXYDataProvider extends AbstractTreeCommonXDataProvider<@NonNull RocmCallStackAnalysis, @NonNull TmfTreeDataModel> {
    /**
     * Data provider ID
     */
    public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.rocm.core.analysis.dataprovider"; //$NON-NLS-1$
    /**
     * Data provider Title
     */
    public static final @NonNull String TITLE = "ROCm Profiling Counters"; //$NON-NLS-1$

    /**
     * @param trace
     *          trace to analyze
     * @param analysisModule
     *          analysis Module from which we get the state system
     */
    public RocmXYDataProvider(@NonNull ITmfTrace trace, @NonNull RocmCallStackAnalysis analysisModule) {
        super(trace, analysisModule);
    }

    @Override
    public @NonNull String getId() {
        return ID;
    }

    @Override
    protected @NonNull String getTitle() {
        return TITLE;
    }

    @Override
    protected boolean isCacheable() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NonNull TmfTreeModel<@NonNull TmfTreeDataModel> getTree(ITmfStateSystem ss, @NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        List<@NonNull TmfTreeDataModel> entries = new ArrayList<>();
        long rootId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        entries.add(new TmfTreeDataModel(rootId, -1, getTrace().getName()));
        addTreeViewerBranch(ss, rootId, "Counters", entries); //$NON-NLS-1$
        return new TmfTreeModel<>(Collections.emptyList(), entries);
    }

    @Override
    protected @Nullable Map<@NonNull String, @NonNull IYModel> getYModels(@NonNull ITmfStateSystem ss, @NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        SelectionTimeQueryFilter filter = FetchParametersUtils.createSelectionTimeQuery(fetchParameters);
        if (filter == null) {
            return null;
        }
        Map<Long, Integer> entries = getSelectedEntries(filter);
        Collection<Long> times = getTimes(filter, ss.getStartTime(), ss.getCurrentEndTime());
        Iterable<@NonNull ITmfStateInterval> query2d = ss.query2D(entries.values(), times);

        TreeMultimap<Integer, ITmfStateInterval> countersIntervals = TreeMultimap.create(Comparator.naturalOrder(),
                Comparator.comparingLong(ITmfStateInterval::getStartTime));

        for (ITmfStateInterval interval : query2d) {
            if (monitor != null && monitor.isCanceled()) {
                return null;
            }
            countersIntervals.put(interval.getAttribute(), interval);
        }

        ImmutableMap.Builder<@NonNull String, @NonNull IYModel> ySeries = ImmutableMap.builder();
        for (Entry<Long, Integer> entry : entries.entrySet()) {
            if (monitor != null && monitor.isCanceled()) {
                return null;
            }
            int quark = entry.getValue();
            int to = 0;
            long[] requestedTimes = filter.getTimesRequested();
            double[] yValues = new double[requestedTimes.length];
            for (ITmfStateInterval interval : countersIntervals.get(quark)) {
                int from = Arrays.binarySearch(requestedTimes, interval.getStartTime());
                from = (from >= 0) ? from : -1 - from;

                Object value = interval.getValue();
                double yValue = value != null ? Double.parseDouble((String) interval.getValue()) : 0.;
                yValues[from] = yValue;

                to = Arrays.binarySearch(requestedTimes, interval.getEndTime());
                to = (to >= 0) ? to + 1 : -1 - to;
                Arrays.fill(yValues, from, to, yValue);
            }
            String attributePath = ss.getFullAttributePath(quark);
            // Making sure that only the performance counters lane will appear.
            // TODO: Fix that issue in a better way...
            if (attributePath.contains("args")) { //$NON-NLS-1$
                String seriesName = getTrace().getName() + '/' + attributePath;
                ySeries.put(seriesName, new YModel(entry.getKey(), seriesName, yValues));
            }
        }
        return ySeries.build();
    }

    private void addTreeViewerBranch(ITmfStateSystem ss, long parentId, @NonNull String branchName, List<TmfTreeDataModel> entries) {
        int quark = ss.optQuarkAbsolute(branchName);
        if (quark != ITmfStateSystem.INVALID_ATTRIBUTE && !ss.getSubAttributes(quark, false).isEmpty()) {
            long id = getId(quark);
            TmfTreeDataModel branch = new TmfTreeDataModel(id, parentId, branchName);
            entries.add(branch);
            addTreeViewerEntries(ss, id, quark, entries);
        }
    }

    /**
     * Recursively add all child entries of a parent branch from the state system.
     */
    private void addTreeViewerEntries(ITmfStateSystem ss, long parentId, int quark, List<TmfTreeDataModel> entries) {
        for (int childQuark : ss.getSubAttributes(quark, false)) {
            long id = getId(childQuark);
            TmfTreeDataModel childBranch = new TmfTreeDataModel(id, parentId, ss.getAttributeName(childQuark));
            entries.add(childBranch);
            addTreeViewerEntries(ss, id, childQuark, entries);
        }
    }

}
