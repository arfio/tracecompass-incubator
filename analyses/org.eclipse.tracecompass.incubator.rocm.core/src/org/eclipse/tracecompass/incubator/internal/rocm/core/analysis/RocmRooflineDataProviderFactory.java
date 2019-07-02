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
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.tmf.core.model.DataProviderDescriptor;
import org.eclipse.tracecompass.internal.tmf.core.model.xy.TmfTreeXYCompositeDataProvider;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderFactory;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor.ProviderType;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfTreeXYDataProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * @author Arnaud Fiorini
 *
 */
public class RocmRooflineDataProviderFactory implements IDataProviderFactory {

    private static final IDataProviderDescriptor DESCRIPTOR = new DataProviderDescriptor.Builder()
            .setId(RocmRooflineDataProvider.ID)
            .setName(RocmRooflineDataProvider.TITLE)
            .setDescription(Objects.requireNonNull("Roofline model analysis of the kernel executions")) //$NON-NLS-1$
            .setProviderType(ProviderType.TREE_TIME_XY)
            .build();

    private static final Predicate<? super ITmfTrace> PREDICATE = t -> TmfTraceUtils.getAnalysisModuleOfClass(t, RocmCallStackAnalysis.class, RocmCallStackAnalysis.ID) != null;

    @Override
    public @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> createProvider(@NonNull ITmfTrace trace) {
        @NonNull Collection<@NonNull ITmfTrace> traces = TmfTraceManager.getTraceSet(trace);
        if (traces.size() == 1) {
            @NonNull List<@NonNull ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel>> dataProviders = new ArrayList<>();
            for (RocmCallStackAnalysis module : TmfTraceUtils.getAnalysisModulesOfClass(trace, RocmCallStackAnalysis.class)) {
                ITmfTrace subTrace = module.getTrace();
                ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> provider = new RocmRooflineDataProvider(Objects.requireNonNull(subTrace), module);
                dataProviders.add(provider);
            }
            if (dataProviders.isEmpty()) {
                return null;
            } else if (dataProviders.size() == 1) {
                return dataProviders.get(0);
            }
            return new TmfTreeXYCompositeDataProvider<>(dataProviders, "Roofline", RocmRooflineDataProvider.ID); //$NON-NLS-1$
        }
        return TmfTreeXYCompositeDataProvider.create(traces, "Roofline", RocmRooflineDataProvider.ID); //$NON-NLS-1$
    }

    @Override
    public Collection<IDataProviderDescriptor> getDescriptors(@NonNull ITmfTrace trace) {
        Collection<ITmfTrace> traces = TmfTraceManager.getTraceSet(trace);
        return Iterables.any(traces, PREDICATE) ? Collections.singletonList(DESCRIPTOR) : Collections.emptyList();
    }
}
