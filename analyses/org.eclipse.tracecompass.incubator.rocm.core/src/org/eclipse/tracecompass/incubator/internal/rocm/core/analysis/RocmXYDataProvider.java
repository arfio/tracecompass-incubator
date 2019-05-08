package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.tmf.core.model.xy.AbstractTreeCommonXDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.model.YModel;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.xy.IYModel;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.ImmutableMap;

/**
 * @author Arnaud Fiorini
 *
 */
@SuppressWarnings("restriction")
public class RocmXYDataProvider extends AbstractTreeCommonXDataProvider<@NonNull RocmCallStackAnalysis, @NonNull TmfTreeDataModel> {
    /**
     * Data provider ID
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.rocm.core.analysis.dataprovider"; //$NON-NLS-1$

    /**
     * @param trace
     *          trace to analyze
     * @param analysisModule
     *          analysis Module from which we get the state system
     */
    public RocmXYDataProvider(ITmfTrace trace, RocmCallStackAnalysis analysisModule) {
        super(trace, analysisModule);
    }

    @Override
    public @NonNull String getId() {
        return ID;
    }

    @Override
    protected @Nullable Map<@NonNull String, @NonNull IYModel> getYModels(@NonNull ITmfStateSystem ss, @NonNull SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        Map<Long, Integer> entries = getSelectedEntries(filter);

        Collection<Long> times = getTimes(filter, ss.getStartTime(), ss.getCurrentEndTime());
        Iterable<@NonNull ITmfStateInterval> query2d = ss.query2D(entries.values(), times);

        ImmutableMap.Builder<String, IYModel> ySeries = ImmutableMap.builder();
        for (Entry<Long, Integer> entry : entries.entrySet()) {
            if (!ss.getSubAttributes(entry.getValue(), false).isEmpty()) {
                continue;
            }
            long[] requestedTimes = filter.getTimesRequested();
            double[] yValues = new double[requestedTimes.length];
            int to = 0;
            for (ITmfStateInterval interval : query2d) {
                int from = Arrays.binarySearch(requestedTimes, interval.getStartTime());
                from = (from >= 0) ? from : -1 - from;

                Integer value = (Integer) interval.getValue();
                double yValue = value != null ? value.doubleValue() : 0.;
                yValues[from] = yValue;

                to = Arrays.binarySearch(requestedTimes, interval.getEndTime());
                to = (to >= 0) ? to + 1 : -1 - to;
                Arrays.fill(yValues, from, to, yValue);
            }
            String seriesName = getTrace().getName() + '/' + ss.getFullAttributePath(entry.getValue());
            ySeries.put(seriesName, new YModel(entry.getKey(), seriesName, yValues));
        }
        return ySeries.build();
    }

    @Override
    protected @NonNull String getTitle() {
        return "Counters"; //$NON-NLS-1$
    }

    @Override
    protected boolean isCacheable() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    protected @NonNull List<@NonNull TmfTreeDataModel> getTree(@NonNull ITmfStateSystem ss, @NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        List<@NonNull TmfTreeDataModel> entries = new ArrayList<>();
        long rootId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        entries.add(new TmfTreeDataModel(rootId, -1, getTrace().getName()));
        addTreeViewerBranch(ss, rootId, "Counters", entries);
        return entries;
    }

    private void addTreeViewerBranch(ITmfStateSystem ss, long parentId, String branchName, List<TmfTreeDataModel> entries) {
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
