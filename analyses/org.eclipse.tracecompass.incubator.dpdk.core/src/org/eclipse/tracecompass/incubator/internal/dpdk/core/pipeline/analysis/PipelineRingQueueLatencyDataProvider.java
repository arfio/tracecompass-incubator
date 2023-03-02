package org.eclipse.tracecompass.incubator.internal.dpdk.core.pipeline.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.dpdk.core.eventdev.analysis.EventDevPerStageLoadPercentageDataProvider;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.model.YModel;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.model.xy.AbstractTreeCommonXDataProvider;
import org.eclipse.tracecompass.tmf.core.model.xy.IYModel;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * This data provider will return a XY model (model is wrapped in a response)
 * based on a query filter. The model is used afterwards by any viewer to draw
 * charts. Model returned is for Network I/O views
 *
 * @author adel
 */
@SuppressWarnings("restriction")
public class PipelineRingQueueLatencyDataProvider extends AbstractTreeCommonXDataProvider<@NonNull DpdkPipelineAnalysisModule, @NonNull TmfTreeDataModel> {

    /**
     * Title used to create XY models for the
     * {@link EventDevPerStageLoadPercentageDataProvider}.
     */
    public static final String PROVIDER_TITLE = Objects.requireNonNull(IDpdkPipelineModelAttributes.IDpdkModel_RING_QUEUE_LATENCY_DATAPROVIDER_TITLE);

    /**
     * Extension point ID.
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.internal.dpdk.core.pipeline.ring.queue.latency.data.provider"; //$NON-NLS-1$

    /**
     * Inline class to encapsulate all the values required to build a series.
     * Allows for reuse of full query results to be faster than {@link}.
     */
    private static final class QueueLatencyBuilder {

        private static final double MICROSECONDS_PER_NANOSECOND = 1E-3;
        private static final double RATIO = 1 / MICROSECONDS_PER_NANOSECOND;

        private final long fId;

        public final int fMeasuredQuark;

        private final String fName;
        private final double[] fValues;
        private long fPrevCount;

        /**
         * Constructor
         *
         * @param name
         *            the series name
         * @param sectorQuark
         *            sector quark
         * @param length
         *            desired length of the series
         */
        private QueueLatencyBuilder(long id, int quark, String name, int length) {
            fId = id;
            fMeasuredQuark = quark;
            fName = name;
            fValues = new double[length];
        }

        private void setPrevCount(long prevCount) {
            fPrevCount = prevCount;
        }

        /**
         * Update the value for the counter at the desired index. Use in
         * increasing order of position
         *
         * @param pos
         *            index to update
         * @param newCount
         *            new received or sent bytes
         * @param deltaT
         *            time difference to the previous value for interpolation
         */
        private void updateValue(int pos, long newCount, long deltaT) {
            /**
             * Linear interpolation to compute.
             */

            long value = 0L;
            if (fPrevCount != newCount) {
                value = newCount;
            }

            fValues[pos] = (value) / RATIO;
            fPrevCount = newCount;
        }

        public IYModel build() {
            return new YModel(fId, fName, fValues);
        }
    }

    /**
     * Create an instance of {@link EventDevPerStageLoadPercentageDataProvider}.
     * Returns a null instance if the analysis module is not found.
     *
     * @param trace
     *            A trace on which we are interested to fetch a model
     * @return A {@link EventDevPerStageLoadPercentageDataProvider} instance. If
     *         analysis module is not found, it returns null
     */
    public static PipelineRingQueueLatencyDataProvider create(ITmfTrace trace) {
        DpdkPipelineAnalysisModule module = TmfTraceUtils.getAnalysisModuleOfClass(trace, DpdkPipelineAnalysisModule.class, DpdkPipelineAnalysisModule.ID);
        if (module != null) {
            module.schedule();
            return new PipelineRingQueueLatencyDataProvider(trace, module);
        }
        return null;
    }

    /**
     * Constructor
     */
    private PipelineRingQueueLatencyDataProvider(ITmfTrace trace, DpdkPipelineAnalysisModule module) {
        super(trace, module);
    }

    /**
     *
     */
    @Override
    public String getId() {
        return ID;
    }

    /**
     *
     */

    @SuppressWarnings("nls")
    @Override
    protected TmfTreeModel<@NonNull TmfTreeDataModel> getTree(ITmfStateSystem ss, Map<String, Object> parameters,
            @Nullable IProgressMonitor monitor) {
        List<TmfTreeDataModel> nodes = new ArrayList<>();

        long rootId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        nodes.add(new TmfTreeDataModel(rootId, -1, getTrace().getName()));

        /* browse the software queues list */
        int ringQueuesQuark = ss.optQuarkRelative(ITmfStateSystem.ROOT_ATTRIBUTE, IDpdkPipelineModelAttributes.SW_QUEUES);
        if (ringQueuesQuark > 0) {
            for (Integer queueQuark : ss.getQuarks(ringQueuesQuark, "*")) {
                String queueName = "" + getQuarkValue(ss, queueQuark);
                int metricQuark;
                try {
                    metricQuark = ss.getQuarkRelative(queueQuark, IDpdkPipelineModelAttributes.QUEUE_LATENCY);
                    long metricId = getId(metricQuark);
                    nodes.add(new TmfTreeDataModel(metricId, rootId, queueName));
                } catch (AttributeNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        return new TmfTreeModel<>(Collections.emptyList(), nodes);
    }

    /**
     *
     * @param ss
     * @param nicQuark
     * @return
     */
    private static String getQuarkValue(ITmfStateSystem ss, Integer nicQuark) {
        ITmfStateInterval interval = StateSystemUtils.queryUntilNonNullValue(ss, nicQuark, ss.getStartTime(), ss.getCurrentEndTime());
        if (interval != null) {
            return String.valueOf(interval.getValue());
        }
        return (ss.getAttributeName(nicQuark));
    }

    // @Override
    // @Deprecated
    // protected Map<String, IYModel> getYModels(ITmfStateSystem ss, Map<String,
    // Object> fetchParameters, @Nullable IProgressMonitor monitor) throws
    // StateSystemDisposedException {
    // return Maps.uniqueIndex(getYSeriesModels(ss, fetchParameters, monitor),
    // IYModel::getName);
    // }

    /**
     * @since 1.2
     */
    @Override
    protected Collection<IYModel> getYSeriesModels(ITmfStateSystem ss, Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor)
            throws StateSystemDisposedException {

        SelectionTimeQueryFilter filter = FetchParametersUtils.createSelectionTimeQuery(fetchParameters);
        if (filter == null) {
            return null;
        }

        long[] xValues = filter.getTimesRequested();
        List<QueueLatencyBuilder> builders = initBuilders(ss, filter);
        if (builders.isEmpty()) {
            return Collections.emptyList();
        }

        long currentEnd = ss.getCurrentEndTime();
        long prevTime = filter.getStart();
        if (prevTime >= ss.getStartTime() && prevTime <= currentEnd) {
            // reuse the results from the full query
            List<ITmfStateInterval> states = ss.queryFullState(prevTime);

            for (QueueLatencyBuilder entry : builders) {
                entry.setPrevCount(extractCount(entry.fMeasuredQuark, states));
            }
        }

        for (int i = 1; i < xValues.length; i++) {
            if (monitor != null && monitor.isCanceled()) {
                return null;
            }
            long time = xValues[i];
            if (time > currentEnd) {
                break;
            } else if (time >= ss.getStartTime()) {
                // reuse the results from the full query
                List<ITmfStateInterval> states = ss.queryFullState(time);

                for (QueueLatencyBuilder entry : builders) {
                    long observationPeriod = time - prevTime;
                    entry.updateValue(i, extractCount(entry.fMeasuredQuark, states), observationPeriod);
                }
            }
            prevTime = time;
        }

        return Maps.uniqueIndex(Iterables.transform(builders, QueueLatencyBuilder::build), IYModel::getName).values();
    }

    /**
     *
     * @param ss
     * @param filter
     * @return
     */
    private List<QueueLatencyBuilder> initBuilders(ITmfStateSystem ss, SelectionTimeQueryFilter filter) {

        int length = filter.getTimesRequested().length;
        List<QueueLatencyBuilder> builders = new ArrayList<>();

        for (Entry<Long, Integer> entry : getSelectedEntries(filter).entrySet()) {

            int metricQuark = entry.getValue();
            String metricName = ss.getAttributeName(metricQuark);

            if (metricName.equals(IDpdkPipelineModelAttributes.QUEUE_LATENCY)) {
                long id = entry.getKey();

                int queueQuark = ss.getParentAttributeQuark(metricQuark);
                String queueName = ss.getAttributeName(queueQuark);

                String name = getTrace().getName() + '/' + queueName;
                builders.add(new QueueLatencyBuilder(id, metricQuark, name, length));
            }
        }
        return builders;
    }

    /**
     * @param recvSentQuark
     *            Quark
     * @param states
     *            Sates
     * @return xx
     */
    public static long extractCount(int metricQuark, List<ITmfStateInterval> states) {

        if (metricQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
            Object occupancyValue = states.get(metricQuark).getValue();

            if (occupancyValue instanceof Number) {
                return ((Number) occupancyValue).longValue();
            }
        }

        return 0L;
    }

    @Override
    protected boolean isCacheable() {
        return true;
    }

    @Override
    protected String getTitle() {
        return PROVIDER_TITLE;
    }

}
