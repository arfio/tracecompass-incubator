/**********************************************************************
 * Copyright (c) 2016 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.dpdk.ui.lookup.object.hit.percentage;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.tmf.ui.viewers.TmfViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.xychart.TmfXYChartViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.xychart.linechart.TmfXYChartSettings;
import org.eclipse.tracecompass.tmf.ui.views.xychart.TmfChartView;

/**
 * Main view to show the Network Activity
 *
 * @author Adel Belkhiri
 */
public class DpdkLookupObjectHitPercentageView extends TmfChartView {

    /** ID string */
    public static final String ID = "org.eclipse.tracecompass.incubator.internal.dpdk.ui.lookup.object.hit.percentage.view"; //$NON-NLS-1$
    private static final double RESOLUTION = 0.3;

    /**
     * Constructor
     */
    public DpdkLookupObjectHitPercentageView() {
        super(Messages.DpdkLookupObjectHitRateView_Title);
    }

    @Override
    protected TmfXYChartViewer createChartViewer(@Nullable Composite parent) {
        TmfXYChartSettings settings = new TmfXYChartSettings(Messages.DpdkLookupObjectHitRateViewer_Title, Messages.DpdkLookupObjectHitRateViewer_XAxis, Messages.DpdkLookupObjectHitRateViewer_YAxis, RESOLUTION);
        return new DpdkLookupObjectHitPercentageViewer(parent, settings);
    }

    @Override
    protected @NonNull TmfViewer createLeftChildViewer(@Nullable Composite parent) {
        return new DpdkLookupObjectHitPercentageTreeViewer(Objects.requireNonNull(parent));
    }
}
