package org.eclipse.tracecompass.incubator.internal.rocm.ui.views;


import java.util.Comparator;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmRooflineScatterDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.TmfViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractSelectTreeViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.TmfXYChartViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.linecharts.TmfFilteredXYChartViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.linecharts.TmfXYChartSettings;
import org.eclipse.tracecompass.tmf.ui.views.TmfChartView;

import com.google.common.collect.ImmutableList;

public class TestLineRocmView extends TmfChartView {

    private static @NonNull String ID = "org.eclipse.tracecompass.analysis.rocm.core.analysis.testview.line"; //$NON-NLS-1$
    /**
     * Constructor
     */
    public TestLineRocmView() {
        super("Tree XY View"); //$NON-NLS-1$
    }

    /**
     * @return ID of the view
     */
    public @NonNull String getId() {
        return ID;
    }

    @Override
    protected TmfXYChartViewer createChartViewer(Composite parent) {
        TmfXYChartSettings settings = new TmfXYChartSettings(null, null, null, 1);
        return new TmfFilteredXYChartViewer(parent, settings, RocmRooflineScatterDataProvider.ID);
    }

    private static final class TreeXyViewer extends AbstractSelectTreeViewer {

        private final class TreeXyLabelProvider extends TreeLabelProvider {
            @Override
            public Image getColumnImage(Object element, int columnIndex) {
                if (columnIndex == 1 && element instanceof TmfTreeViewerEntry && isChecked(element)) {
                    return getLegendImage(((TmfTreeViewerEntry) element).getName());
                }
                return null;
            }
        }

        public TreeXyViewer(Composite parent) {
            super(parent, 1, RocmRooflineScatterDataProvider.ID);
            setLabelProvider(new TreeXyLabelProvider());
        }

        @Override
        protected ITmfTreeColumnDataProvider getColumnDataProvider() {
            return () -> ImmutableList.of(createColumn("Name", Comparator.comparing(TmfTreeViewerEntry::getName)), //$NON-NLS-1$
                    new TmfTreeColumnData("Legend")); //$NON-NLS-1$
        }
    }

    @Override
    protected @NonNull TmfViewer createLeftChildViewer(Composite parent) {
        return new TreeXyViewer(parent);
    }
}
