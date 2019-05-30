package org.eclipse.tracecompass.incubator.internal.rocm.ui.views;


import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.scatter.AbstractSegmentStoreScatterChartViewer;
import org.eclipse.tracecompass.incubator.internal.rocm.core.analysis.RocmCallStackAnalysis;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.linecharts.TmfXYChartSettings;

public class TestScatterRocmView extends AbstractSegmentStoreScatterChartViewer {

    private static @NonNull String ID = "org.eclipse.tracecompass.analysis.rocm.core.analysis.testview.scatter"; //$NON-NLS-1$

    /**
     * Constructor
     * @param parent
     *            parent composite
     * @param title
     *            name of the graph
     * @param xLabel
     *            name of the x axis
     * @param yLabel
     *            name of the y axis
     */
    public TestScatterRocmView(Composite parent, String title, String xLabel, String yLabel) {
        super(parent, new TmfXYChartSettings(title, xLabel, yLabel, 1), RocmCallStackAnalysis.ID);
    }

    /**
     * @return ID of the view
     */
    public @NonNull String getId() {
        return ID;
    }

//    @Override
//    protected TmfXYChartViewer createChartViewer(Composite parent) {
//        TmfXYChartSettings settings = new TmfXYChartSettings(null, null, null, 1);
//        return new TmfFilteredXYChartViewer(parent, settings, RocmRooflineScatterDataProvider.ID);
//    }

//    private static final class TreeXyViewer extends AbstractSelectTreeViewer {
//
//        private final class TreeXyLabelProvider extends TreeLabelProvider {
//            @Override
//            public Image getColumnImage(Object element, int columnIndex) {
//                if (columnIndex == 1 && element instanceof TmfTreeViewerEntry && isChecked(element)) {
//                    return getLegendImage(((TmfTreeViewerEntry) element).getName());
//                }
//                return null;
//            }
//        }
//
//        public TreeXyViewer(Composite parent) {
//            super(parent, 1, RocmRooflineScatterDataProvider.ID);
//            setLabelProvider(new TreeXyLabelProvider());
//        }
//
//        @Override
//        protected ITmfTreeColumnDataProvider getColumnDataProvider() {
//            return () -> ImmutableList.of(createColumn("Name", Comparator.comparing(TmfTreeViewerEntry::getName)), //$NON-NLS-1$
//                    new TmfTreeColumnData("Legend")); //$NON-NLS-1$
//        }
//    }

//    @Override
//    protected @NonNull TmfViewer createLeftChildViewer(Composite parent) {
//        return new TreeXyViewer(parent);
//    }
}
