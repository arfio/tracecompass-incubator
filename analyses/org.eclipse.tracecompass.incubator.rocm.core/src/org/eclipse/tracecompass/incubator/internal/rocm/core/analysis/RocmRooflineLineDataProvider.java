/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.rocm.core.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
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
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.YModel;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfTreeXYDataProvider;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfXyModel;
import org.eclipse.tracecompass.tmf.core.model.xy.IYModel;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

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
public class RocmRooflineLineDataProvider extends AbstractTreeDataProvider<@NonNull RocmCallStackAnalysis, @NonNull TmfTreeDataModel> implements ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> {
    /**
     * Data provider ID
     */
    public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.rocm.core.analysis.roofline.linedataprovider"; //$NON-NLS-1$
    /**
     * Trace properties
     */
    protected @NonNull Map<@NonNull String, @NonNull String> fTraceProperties = new LinkedHashMap<>();
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
    public RocmRooflineLineDataProvider(@NonNull ITmfTrace trace, @NonNull RocmCallStackAnalysis analysisModule) {
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

        JsonObject gpuInfo = getGpuInfo();
        if (gpuInfo == null) {
            return TmfXyResponseFactory.createFailedResponse("No gpu metada has been found."); //$NON-NLS-1$
        }

        String prefix = getTrace().getName() + "/"; //$NON-NLS-1$
        long[] xValues = getXValues();
        ImmutableMap.Builder<@NonNull String, @NonNull IYModel> xySeries = ImmutableMap.builder();
        Map<Long, Integer> entries = getSelectedEntries((SelectionTimeQueryFilter) filter);
        for (Entry<Long, Integer> entry : entries.entrySet()) {
            if (monitor != null && monitor.isCanceled()) {
                return TmfXyResponseFactory.createCancelledResponse(CommonStatusMessage.TASK_CANCELLED);
            }
            if (gpuInfo.has(entry.getValue().toString())) {
                String seriesName = prefix + ss.getFullAttributePath(entry.getValue());
                JsonObject gpu = gpuInfo.get(entry.getValue().toString()).getAsJsonObject();
                xySeries.put(seriesName, new YModel(entry.getValue(), seriesName, calculateRoofline(
                        gpu.get("mem-bandwidth").getAsDouble(), gpu.get("max-vec-insts").getAsDouble(), xValues //$NON-NLS-1$ //$NON-NLS-2$
                )));
            }
        }
        return TmfXyResponseFactory.create(getTrace().getName(), xValues, xySeries.build(), true);
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

    @Override
    protected boolean isCacheable() {
        return false;
    }

    @Override
    protected List<TmfTreeDataModel> getTree(@NonNull ITmfStateSystem ss, @NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        Builder<TmfTreeDataModel> nodes = new ImmutableList.Builder<>();
        nodes.add(new TmfTreeDataModel(getId(-1), -1, String.valueOf(getTrace().getName())));

        JsonObject gpuInfo = getGpuInfo();

        int processesQuark = getAndAddQuarkRelative(-1, "Processes", ss, nodes); //$NON-NLS-1$
        int gpuListQuark = getAndAddQuarkRelative(processesQuark, "GPU", ss, nodes); //$NON-NLS-1$
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
                    getAndAddQuarkRelative(gpuListQuark, gpuId, ss, nodes);
                }
            }
            return nodes.build();
        }
        return Collections.emptyList();
    }

    private int getAndAddQuarkRelative(int parent, @NonNull String childName, @NonNull ITmfStateSystem ss, Builder<TmfTreeDataModel> nodes) {
        try {
            int childQuark = ss.getQuarkRelative(parent, childName);
            long parentId = getId(parent);
            long id = getId(childQuark);

            TmfTreeDataModel branch = new TmfTreeDataModel(id, parentId, childName);
            nodes.add(branch);

            return childQuark;
        } catch (AttributeNotFoundException e) {
            return ITmfStateSystem.INVALID_ATTRIBUTE;
        }
    }

    private static long @NonNull[] getXValues() {
        long[] xValues = new long[100];
        for (int i = 0; i < 100; i++) {
            xValues[i] = i;
        }
        return xValues;
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
        return "Line Data Provider"; //$NON-NLS-1$
    }
}
