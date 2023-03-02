/*******************************************************************************
 * Copyright (c) 2017, 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.dpdk.ui.eventdev.inflight.credit;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.incubator.internal.dpdk.core.eventdev.analysis.EventDevInflightCreditDataProvider;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractSelectTreeViewer2;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfGenericTreeEntry;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;

/**
 * Activity Tree Viewer
 *
 * @author adel
 */
public class DpdkEventDevInflightCreditTreeViewer extends AbstractSelectTreeViewer2 {

    private final class NetworkTreeLabelProvider extends TreeLabelProvider {

        @Override
        public @Nullable Image getColumnImage(@Nullable Object element, int columnIndex) {
            if (columnIndex == 1 && element instanceof TmfGenericTreeEntry && isChecked(element)) {
                TmfGenericTreeEntry<TmfTreeDataModel> entry = (TmfGenericTreeEntry<TmfTreeDataModel>) element;
                if (!entry.hasChildren()) {
                    return getLegendImage(entry.getModel().getId());
                }
            }
            return null;
        }
    }

    /**
     * Constructor
     *
     * @param parent
     *            Parent composite
     */
    public DpdkEventDevInflightCreditTreeViewer(Composite parent) {
        super(parent, 1, EventDevInflightCreditDataProvider.ID);
        setLabelProvider(new NetworkTreeLabelProvider());
    }

    @Override
    protected ITmfTreeColumnDataProvider getColumnDataProvider() {
        return () -> {
            return ImmutableList.of(
                    createColumn(Messages.DpdkEventDevInflightCreditTreeViewer_EventDevName, Comparator.comparing(TmfGenericTreeEntry::getName)),
                    new TmfTreeColumnData(Messages.DpdkEventDevInflightCreditTreeViewer_Legend));
        };
    }

    @Override
    public void updateContent(long start, long end, boolean isSelection) {
        super.updateContent(start, end, isSelection);
    }
}
