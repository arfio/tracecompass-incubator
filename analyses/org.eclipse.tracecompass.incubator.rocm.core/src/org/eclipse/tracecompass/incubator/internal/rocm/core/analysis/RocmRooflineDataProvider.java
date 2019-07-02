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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.provisional.jsontrace.core.trace.JsonTrace;
import org.eclipse.tracecompass.internal.tmf.core.model.TmfXyResponseFactory;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.internal.tmf.core.model.tree.AbstractTreeDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.SeriesModel;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ISeriesModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfTreeXYDataProvider;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfXyModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ISeriesModel.DisplayType;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


/**
 * @author Arnaud Fiorini
 *
 */
@SuppressWarnings("restriction")
public class RocmRooflineDataProvider extends AbstractTreeDataProvider<@NonNull RocmCallStackAnalysis, @NonNull TmfTreeDataModel> implements ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> {
    /**
     * Data provider ID
     */
    public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.rocm.core.analysis.roofline.dataprovider"; //$NON-NLS-1$
    /**
     * Data provider title
     */
    public static final @NonNull String TITLE = "ROCm Roofline"; //$NON-NLS-1$
    /**
     * Trace properties
     */
    protected @NonNull Map<@NonNull String, @NonNull String> fTraceProperties = new LinkedHashMap<>();
    /**
     * Json parser
     */
    private static final Gson G_SON = new Gson();
    /**
     * Map linking every gpu kernel executions to one kernel name (key1: gpu id, key2: kernel name, value: interval list)
     */
    Table<String, String, List<@NonNull ITmfStateInterval>> fGpuKernelIntervalMap;
    /**
     * Map each kernel of each gpu to the lane id
     */
    Table<String, String, Long> fGpuKernelLaneIdMap = HashBasedTable.create();

    /**
     * @param trace
     *          trace to analyze
     * @param analysisModule
     *          analysis module from which we get the state system
     */
    public RocmRooflineDataProvider(@NonNull ITmfTrace trace, @NonNull RocmCallStackAnalysis analysisModule) {
        super(trace, analysisModule);
        if (trace instanceof JsonTrace) {
            fTraceProperties = ((JsonTrace) trace).getProperties();
        }
    }

    @Override
    public @NonNull String getId() {
        return ID;
    }

    @Override
    protected boolean isCacheable() {
        return false;
    }

    @Override
    protected @NonNull TmfTreeModel<@NonNull TmfTreeDataModel> getTree(@NonNull ITmfStateSystem ss, @NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        Builder<@NonNull TmfTreeDataModel> nodes = new ImmutableList.Builder<>();
        nodes.add(new TmfTreeDataModel(getId(-1), -1, String.valueOf(getTrace().getName())));

        JsonObject gpuInfo = getGpuInfo();

        int processesQuark = getAndAddQuarkRelative(ss, -1, "Processes", nodes); //$NON-NLS-1$
        int gpuListQuark = getAndAddQuarkRelative(ss, processesQuark, "GPU", nodes); //$NON-NLS-1$
        if (processesQuark != ITmfStateSystem.INVALID_ATTRIBUTE &&
              gpuListQuark != ITmfStateSystem.INVALID_ATTRIBUTE &&
                   gpuInfo != null)
        {
            Table<String, String, List<@NonNull ITmfStateInterval>> gpuKernelIntervalMap = getGpuKernelIntervalMap(ss, monitor);
            for (Entry<@NonNull String, JsonElement> gpu : gpuInfo.entrySet()) {
                if (monitor != null && monitor.isCanceled()) {
                    return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
                }
                int gpuQuark = getAndAddQuarkRelative(ss, gpuListQuark, gpu.getKey(), nodes);
                // Get all kernel names that happened on this gpu and add an entry for each
                Set<@NonNull String> kernelNames = gpuKernelIntervalMap.row(gpu.getKey()).keySet();
                for (String kernelName : kernelNames) {
                    long kernelGpuId = getEntryId();
                    fGpuKernelLaneIdMap.put(gpu.getKey(), kernelName, kernelGpuId);
                    nodes.add(new TmfTreeDataModel(kernelGpuId, getId(gpuQuark), kernelName));
                }
            }
            return new TmfTreeModel<>(Collections.emptyList(), nodes.build());
        }
        return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
    }

    private int getAndAddQuarkRelative(@NonNull ITmfStateSystem ss, int parentQuark, @NonNull String childName, Builder<TmfTreeDataModel> nodes) {
        try {
            int quark = ss.getQuarkRelative(parentQuark, childName);
            addQuarkRelative(ss, parentQuark, quark, nodes);
            return quark;
        } catch (AttributeNotFoundException e) {
            return ITmfStateSystem.INVALID_ATTRIBUTE;
        }
    }

    private void addQuarkRelative(@NonNull ITmfStateSystem ss, int parentQuark, int quark, Builder<TmfTreeDataModel> nodes) {
        long id = getId(quark);
        long parentId = getId(parentQuark);
        TmfTreeDataModel childBranch = new TmfTreeDataModel(id, parentId, ss.getAttributeName(quark));
        nodes.add(childBranch);
    }

    @Override
    public TmfModelResponse<ITmfXyModel> fetchXY(Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        RocmCallStackAnalysis module = getAnalysisModule();
        SelectionTimeQueryFilter filter = FetchParametersUtils.createSelectionTimeQuery(fetchParameters);
        if (filter == null) {
            return TmfXyResponseFactory.createFailedResponse("Filter should not be null"); //$NON-NLS-1$
        }
        TmfModelResponse<@NonNull ITmfXyModel> res = verifyParameters(module, filter, monitor);
        if (res != null) {
            return res;
        }

        @NonNull ITmfStateSystem ss = Objects.requireNonNull(module.getStateSystem(),
                "Statesystem should have been verified by verifyParameters"); //$NON-NLS-1$

        JsonObject gpuInfo = getGpuInfo();
        if (gpuInfo == null) {
            return TmfXyResponseFactory.createFailedResponse("No gpu metada has been found."); //$NON-NLS-1$
        }
        int gpuListQuark = ss.optQuarkAbsolute("Processes", "GPU"); //$NON-NLS-1$ //$NON-NLS-2$
        if (gpuListQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return TmfXyResponseFactory.createFailedResponse("No gpu lanes have been found."); //$NON-NLS-1$
        }
        Table<String, String, List<@NonNull ITmfStateInterval>> table = getGpuKernelIntervalMap(ss, monitor);
        ImmutableMap.Builder<@NonNull String, @NonNull ISeriesModel> seriesModels = ImmutableMap.builder();
        String prefix = getTrace().getName() + "/"; //$NON-NLS-1$

        for (String gpuKey : table.rowKeySet()) {
            if (monitor != null && monitor.isCanceled()) {
                return TmfXyResponseFactory.createCancelledResponse(CommonStatusMessage.TASK_CANCELLED);
            }
            for (Entry<String, List<ITmfStateInterval>> entry: table.row(gpuKey).entrySet()) {
                // get each kernel performance counters and plot points
                Long laneId = Objects.requireNonNull(fGpuKernelLaneIdMap.get(gpuKey, entry.getKey()));
                List<ITmfStateInterval> intervals = entry.getValue();
                long[] xValues = new long[intervals.size()];
                double[] yValues = new double[intervals.size()];

                // Get the performance counters quarks
                int vMemQuark = getPerfCounterQuark(ss, "FetchSize"); //$NON-NLS-1$
                int vecInstsQuark = getPerfCounterQuark(ss, "VALUInsts"); //$NON-NLS-1$
                if (vMemQuark == ITmfStateSystem.INVALID_ATTRIBUTE && vecInstsQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
                    return TmfXyResponseFactory.createFailedResponse("The analysis failed to find the needed performance counters (VMemInsts, VecInsts)."); //$NON-NLS-1$
                }
                for (int i = 0; i < intervals.size(); i++) {
                    long time = intervals.get(i).getEndTime() + 1;
                    if (time >= ss.getCurrentEndTime()) {
                        continue;
                    }
                    Double memTransfer = Double.parseDouble(getPerfCounterValue(ss, vMemQuark, time));
                    Double vecInsts = Double.parseDouble(getPerfCounterValue(ss, vecInstsQuark, time));
                    Long xValue = Double.valueOf(100000000 * (vecInsts / memTransfer)).longValue();
                    xValues[i] = xValue;
                    yValues[i] = vecInsts;
                }
                String seriesName = prefix + entry.getKey() + gpuKey;
                SeriesModel seriesModelObject = new SeriesModel.SeriesModelBuilder(laneId, seriesName, xValues, yValues)
                        .seriesDisplayType(DisplayType.SCATTER)
                        .build();
                seriesModels.put(seriesName, seriesModelObject);
            }
            // Bandwidth and compute bound line
            long[] xValues = getXValues();
            if (gpuInfo.has(gpuKey)) {
                String seriesName = prefix + "bound" + gpuKey; //$NON-NLS-1$
                JsonObject gpu = gpuInfo.get(gpuKey).getAsJsonObject();
                int gpuQuark = ss.optQuarkRelative(gpuListQuark, gpuKey);
                if (gpuQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                    SeriesModel seriesModelObject = new SeriesModel.SeriesModelBuilder(getId(gpuQuark), seriesName, xValues, calculateRoofline(
                            gpu.get("mem-bandwidth").getAsDouble(), gpu.get("max-vec-insts").getAsDouble(), xValues //$NON-NLS-1$ //$NON-NLS-2$
                            ))
                            .build();
                    seriesModels.put(seriesName, seriesModelObject);
                }
            }
        }
        return TmfXyResponseFactory.create(getTrace().getName(), seriesModels.build(), true);
    }

    private static String getPerfCounterValue(@NonNull ITmfStateSystem ss, int perfCounterQuark, long time) {
        String perfCounterValue = "0"; //$NON-NLS-1$
        try {
            perfCounterValue = (String) ss.querySingleState(time, perfCounterQuark).getValue();
        } catch (StateSystemDisposedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return perfCounterValue;
    }

    private static int getPerfCounterQuark(@NonNull ITmfStateSystem ss, String perfCounter) {
        int perfCounterQuark = ITmfStateSystem.INVALID_ATTRIBUTE;
        try {
            perfCounterQuark = ss.getQuarkAbsolute("Counters", "0", "args/" + perfCounter); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        } catch (AttributeNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return perfCounterQuark;
    }

    private Table<String, String, List<@NonNull ITmfStateInterval>> getGpuKernelIntervalMap(@NonNull ITmfStateSystem ss, @Nullable IProgressMonitor monitor) {
        if (fGpuKernelIntervalMap != null) {
            return fGpuKernelIntervalMap;
        }
        try {
            // query all interval of all the gpu quarks
            int gpuListQuark = ss.getQuarkAbsolute("Processes", "GPU"); //$NON-NLS-1$ //$NON-NLS-2$
            Collection<Integer> kernelQuarks = getLeafQuarks(ss, gpuListQuark);
            Iterable<@NonNull ITmfStateInterval> gpuIntervals = ss.query2D(kernelQuarks, ss.getStartTime(), ss.getCurrentEndTime());
            Table<String, String, List<@NonNull ITmfStateInterval>> gpuKernelIntervalMap = HashBasedTable.create();

            // loop through every intervals and construct the table which has two keys: gpuId and kernelName
            for (ITmfStateInterval interval : gpuIntervals) {
                if (monitor != null && monitor.isCanceled()) {
                    return gpuKernelIntervalMap;
                }
                if (interval.getStateValue().getType().equals(ITmfStateValue.Type.STRING)) {
                    // This interval is in a callstack so to get the parent we need to go up twice.
                    int callstackQuark = ss.getParentAttributeQuark(interval.getAttribute());
                    int gpuQuark = ss.getParentAttributeQuark(callstackQuark);
                    Object gpuId = ss.querySingleState(interval.getStartTime(), gpuQuark).getValue();
                    if (gpuId == null) {
                        continue;
                    }
                    List<@NonNull ITmfStateInterval> intervalList = gpuKernelIntervalMap.get(gpuId, interval.getStateValue().toString());
                    if (intervalList == null) {
                        intervalList = new LinkedList<>();
                    }
                    intervalList.add(interval);

                    gpuKernelIntervalMap.put((String) gpuId, interval.getStateValue().toString(), intervalList);
                }
            }
            fGpuKernelIntervalMap = gpuKernelIntervalMap;
        } catch (IndexOutOfBoundsException | TimeRangeException | StateSystemDisposedException | AttributeNotFoundException e) {
            e.printStackTrace();
        }
        return fGpuKernelIntervalMap;
    }

    private @NonNull Collection<Integer> getLeafQuarks(@NonNull ITmfStateSystem ss, int parentQuark) {
        Collection<Integer> children = ss.getSubAttributes(parentQuark, false);
        Collection<Integer> leafQuarks = new ArrayList<>();
        if (children.isEmpty()) {
            leafQuarks.add(parentQuark);
        }
        for (int childQuark : children) {
            leafQuarks.addAll(getLeafQuarks(ss, childQuark));
        }
        return leafQuarks;
    }

    private static long @NonNull[] getXValues() {
        long[] xValues = new long[100];
        for (int i = 0; i < 100; i++) {
            xValues[i] = i;
        }
        return xValues;
    }

    private static double[] calculateRoofline(double maxBandwidth, double peakPerformance, long @NonNull[] xValues) {
        double[] bandwidthLine = new double[xValues.length];
        for (int i = 0; i < xValues.length; i++) {
            bandwidthLine[i] = maxBandwidth * i;
            if (bandwidthLine[i] > peakPerformance) {
                bandwidthLine[i] = peakPerformance;
            }
        }
        return bandwidthLine;
    }

    private @Nullable JsonObject getGpuInfo() {
        // Save gpu information if it exists in the trace properties
        ITmfTrace trace = getTrace();
        if (trace instanceof JsonTrace) {
            Map<@NonNull String, @NonNull String> traceProperties = ((JsonTrace) trace).getProperties();
            String gpuInfo = traceProperties.get("gpu_info"); //$NON-NLS-1$
            if (gpuInfo != null) {
                return G_SON.fromJson(gpuInfo, JsonObject.class);
            }
        }
        return null;
    }

    @Deprecated
    @Override
    public @NonNull TmfModelResponse<@NonNull ITmfXyModel> fetchXY(@NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        return TmfXyResponseFactory.createFailedResponse("Deprecated"); //$NON-NLS-1$
    }
}
