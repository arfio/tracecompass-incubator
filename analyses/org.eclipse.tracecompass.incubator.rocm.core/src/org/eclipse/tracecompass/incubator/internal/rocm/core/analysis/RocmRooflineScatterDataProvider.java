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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.provisional.jsontrace.core.trace.JsonTrace;
import org.eclipse.tracecompass.internal.tmf.core.model.TmfXyResponseFactory;
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
import org.eclipse.tracecompass.tmf.core.model.xy.ISeriesModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfTreeXYDataProvider;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfXyModel;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.util.Pair;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @author Arnaud Fiorini
 *
 */
@SuppressWarnings("restriction")
public class RocmRooflineScatterDataProvider extends AbstractTreeDataProvider<@NonNull RocmCallStackAnalysis, @NonNull TmfTreeDataModel> implements ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> {
    /**
     * Data provider ID
     */
    public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.rocm.core.analysis.roofline.scatterdataprovider"; //$NON-NLS-1$
    /**
     * Json parser
     */
    private static final Gson G_SON = new Gson();

    /**
     * @param trace
     *          trace to analyze
     * @param analysisModule
     *          analysis Module from which we get the state system
     */
    public RocmRooflineScatterDataProvider(@NonNull ITmfTrace trace, @NonNull RocmCallStackAnalysis analysisModule) {
        super(trace, analysisModule);
    }

    @Override
    public @NonNull String getId() {
        return ID;
    }

    @Override
    public TmfModelResponse<@NonNull ITmfXyModel> fetchXY(@NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        RocmCallStackAnalysis module = getAnalysisModule();
        TmfModelResponse<@NonNull ITmfXyModel> res = verifyParameters(module, filter, monitor);
        if (res != null) {
            return res;
        }

        @NonNull ITmfStateSystem ss = Objects.requireNonNull(module.getStateSystem(),
                "Statesystem should have been verified by verifyParameters"); //$NON-NLS-1$

        long currentEnd = ss.getCurrentEndTime();
        boolean complete = ss.waitUntilBuilt(0) || filter.getEnd() <= currentEnd;
        if (!(filter instanceof SelectionTimeQueryFilter)) {
            return TmfXyResponseFactory.create(getTitle(), filter.getTimesRequested(), Collections.emptyMap(), complete);
        }

        Map<Long, Integer> entries = getSelectedEntries((SelectionTimeQueryFilter) filter);
        Map<Integer, Pair<List<Long>, List<Double>>> seriesModelMap = new HashMap<>();
        try {
            // Query all the intervals for all the gpu entries, get the performance counters quarks
            Iterable<@NonNull ITmfStateInterval> query2d = ss.query2D(entries.values(), ss.getStartTime(), ss.getCurrentEndTime());
            int vMemQuark = ss.getQuarkAbsolute("Counters", "0", "args/FetchSize"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            int vecInstsQuark = ss.getQuarkAbsolute("Counters", "0", "args/VALUInsts"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (vMemQuark == ITmfStateSystem.INVALID_ATTRIBUTE && vecInstsQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
                return TmfXyResponseFactory.createFailedResponse("The analysis failed to find the needed performance counters (VMemInsts, VecInsts)."); //$NON-NLS-1$
            }
            // Loop through the intervals, getting the performance counters each time there is a kernel execution
            for (ITmfStateInterval interval : query2d) {
                if (monitor != null && monitor.isCanceled()) {
                    return TmfXyResponseFactory.createCancelledResponse(CommonStatusMessage.TASK_CANCELLED);
                }
                if (interval.getStateValue().getType().equals(ITmfStateValue.Type.STRING)) {
                    long time = interval.getEndTime() + 1;
                    if (time >= ss.getCurrentEndTime()) {
                        continue;
                    }
                    // get Perf counters values
                    Double memTransfer = Double.parseDouble((String) ss.querySingleState(time, vMemQuark).getValue());
                    Double vecInsts = Double.parseDouble((String) ss.querySingleState(time, vecInstsQuark).getValue());
                    Pair<List<Long>, List<Double>> seriesModel = seriesModelMap.get(interval.getAttribute());
                    if (seriesModel == null) {
                        seriesModel = new Pair<>(new ArrayList<>(), new ArrayList<>());
                        seriesModelMap.put(interval.getAttribute(), seriesModel);
                    }
                    seriesModel.getFirst().add(Double.doubleToLongBits(vecInsts / memTransfer));
                    seriesModel.getSecond().add(vecInsts);
                }
            }
        } catch (StateSystemDisposedException | TimeRangeException | IndexOutOfBoundsException | AttributeNotFoundException e) {
            return TmfXyResponseFactory.createFailedResponse(String.valueOf(e.getMessage()));
        }
        // Loop through every entry (i.e. gpu) to create a series model converting the list into arrays
        ImmutableMap.Builder<@NonNull String, @NonNull ISeriesModel> seriesModels = ImmutableMap.builder();
        String prefix = getTrace().getName() + "/"; //$NON-NLS-1$
        for (Entry<Long, Integer> entry : entries.entrySet()) {
            int quark = entry.getValue();
            Pair<List<Long>, List<Double>> seriesModel = seriesModelMap.get(quark);
            if (seriesModel == null) {
                continue;
            }
            double[] yValues = new double[seriesModel.getSecond().size()];
            long[] xValues = new long[seriesModel.getFirst().size()];
            for (int i = 0; i < seriesModel.getFirst().size(); i++) {
                xValues[i] = seriesModel.getFirst().get(i);
                yValues[i] = seriesModel.getSecond().get(i);
            }
            String seriesName = prefix + ss.getFullAttributePath(quark);
            seriesModels.put(seriesName, new SeriesModel(entry.getKey(), seriesName, xValues, yValues));
        }
        return TmfXyResponseFactory.create(getTrace().getName(), seriesModels.build(), true);
    }

    @Override
    protected boolean isCacheable() {
        return true;
    }

    @Override
    public List<TmfTreeDataModel> getTree(ITmfStateSystem ss, TimeQueryFilter filter,
            @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        Builder<TmfTreeDataModel> nodes = new ImmutableList.Builder<>();
        nodes.add(new TmfTreeDataModel(getId(-1), -1, String.valueOf(getTrace().getName())));

        JsonObject gpuInfo = getGpuInfo();

        int processesQuark = getAndAddQuarkRelative(ss, -1, "Processes", nodes); //$NON-NLS-1$
        int gpuListQuark = getAndAddQuarkRelative(ss, processesQuark, "GPU", nodes); //$NON-NLS-1$
        if (processesQuark != ITmfStateSystem.INVALID_ATTRIBUTE &&
              gpuListQuark != ITmfStateSystem.INVALID_ATTRIBUTE &&
                   gpuInfo != null)
        {
            for (Entry<String, JsonElement> gpu : gpuInfo.entrySet()) {
                if (monitor != null && monitor.isCanceled()) {
                    return Collections.emptyList();
                }
                String gpuId = gpu.getKey();
                if (gpuId != null) {
                    int gpuQuark = getAndAddQuarkRelative(ss, gpuListQuark, gpuId, nodes);
                    addTreeViewerEntries(ss, gpuQuark, nodes);
                }
            }
            return nodes.build();
        }
        return Collections.emptyList();
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

    /**
     * Recursively add all child entries of a parent branch from the state system.
     */
    private void addTreeViewerEntries(@NonNull ITmfStateSystem ss, int parentQuark, Builder<TmfTreeDataModel> nodes) {
        for (int quark : ss.getSubAttributes(parentQuark, false)) {
            addQuarkRelative(ss, parentQuark, quark, nodes);
            addTreeViewerEntries(ss, quark, nodes);
        }
    }

    private void addQuarkRelative(@NonNull ITmfStateSystem ss, int parentQuark, int quark, Builder<TmfTreeDataModel> nodes) {
        long id = getId(quark);
        long parentId = getId(parentQuark);
        TmfTreeDataModel childBranch = new TmfTreeDataModel(id, parentId, ss.getAttributeName(quark));
        nodes.add(childBranch);
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

    private static @NonNull String getTitle() {
        return "Scatter Data Provider"; //$NON-NLS-1$
    }

}