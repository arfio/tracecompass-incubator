package org.eclipse.tracecompass.incubator.internal.benchmark.sample.core.benchmark;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.eclipse.tracecompass.incubator.benchmark.sample.core.trace.MonotoneTrace;
import org.eclipse.tracecompass.incubator.internal.benchmark.sample.core.analysis.MonotoneAnalysis;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.tests.stubs.backend.HistoryTreeBackendStub;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfAnalysisModuleWithStateSystems;
import org.eclipse.tracecompass.tmf.core.tests.shared.TmfTestHelper;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.junit.Test;
import static org.junit.Assert.fail;

public class TraceStateHistoryTreeBenchmark {
    /**
     * Test test ID for the analysis benchmarks
     */
    public static final String TEST_ID = "org.eclipse.tracecompass# Trace history tree test#";
    private static final String TEST_CPU = "CPU Usage (%s)";
    private static final int LOOP_COUNT = 1;
    /** Default maximum number of children nodes */
    //private static final int MAX_CHILDREN = 3;
    /** Default block size */
    //private static final int BLOCK_SIZE = 4096;
    /** Provider version */
    private static final int PROVIDER_VERSION = 0;

    private interface runMethod {
        void execute(PerformanceMeter pm, ITmfAnalysisModuleWithStateSystems module);
    }

    private runMethod cpu = (pm, module) -> {
        pm.start();
        TmfTestHelper.executeAnalysis(module);
        pm.stop();
    };

    /**
     * Runs all the benchmarks
     *
     * @throws StateSystemDisposedException
     * @throws AttributeNotFoundException
     * @throws TmfTraceException
     */
    @Test
    public void runAllBenchmarks() throws AttributeNotFoundException, StateSystemDisposedException, TmfTraceException {

        // Here, you can specify which analysis module you want to test by
        // replacing the "KernelAnalysisModule()"
        Supplier<IAnalysisModule> moduleSupplier = () -> new MonotoneAnalysis();
        String directoryPath = "/home/arnaud/Documents/test_traces/monotone-vs-intervals/big_opencl_rocm_trace/CTF_trace";
        File parentDirectory = new File(directoryPath);
        if (!parentDirectory.isDirectory() || parentDirectory.list() == null) {
            System.err.println(String.format("Trace directory not found !\nYou need to setup the directory path before "
                    + "running this benchmark. See the javadoc of this class."));
            return;
        }
        MonotoneTrace trace = new MonotoneTrace();
        trace.initTrace(null, parentDirectory.getPath(), CtfTmfEvent.class);
        runOneBenchmark(trace,
                String.format(TEST_CPU, trace.toString()),
                cpu, Dimension.CPU_TIME, moduleSupplier);
    }

    private static void runOneBenchmark(@NonNull CtfTmfTrace testTrace, String testName, runMethod method,
            Dimension dimension, Supplier<IAnalysisModule> moduleSupplier) throws AttributeNotFoundException, StateSystemDisposedException {

        Performance perf = Performance.getDefault();
        PerformanceMeter pm = perf.createPerformanceMeter(TEST_ID + testName);
        perf.tagAsSummary(pm, "Trace Compass Analysis " + testName, dimension);

        for (int i = 0; i < LOOP_COUNT; i++) {
            CtfTmfTrace trace = null;

            MonotoneAnalysis module = null;

            String path = testTrace.getPath();
            try {
                trace = new CtfTmfTrace();

                module = (MonotoneAnalysis) moduleSupplier.get();
                module.setId("traceTest");
                trace.initTrace(null, path, CtfTmfEvent.class);

                module.setTrace(trace);
                method.execute(pm, module);
                Iterable<@NonNull ITmfStateSystem> analysisStateSystems = module.getStateSystems();


                // Creating the history tree state system and printing its information
                ITmfStateSystem analysisStateSystem = analysisStateSystems.iterator().next();
                String ssid = analysisStateSystem.getSSID();


                File historyTreeFile = module.getSsFile();
                PrintWriter writer = new PrintWriter(System.out, true);
                try {
                    //historyTreeFile = NonNullUtils.checkNotNull(File.createTempFile("TraceHistoryTreeBenchmark", ".ht"));
                    HistoryTreeBackendStub backend = new HistoryTreeBackendStub(ssid, historyTreeFile, PROVIDER_VERSION);
                    // Printing the information on the state history Tree
                    backend.debugPrint(writer, false, -1);

                    pm.commit();
                    perf.assertPerformance(pm);
                    pm.dispose();
                } catch (IOException e) {
                }

                deleteSupplementaryFiles(trace);

            } catch (TmfAnalysisException | TmfTraceException e) {
                fail(e.getMessage());
            } finally {

                if (module != null) {
                    module.dispose();
                }
                if (trace != null) {
                    trace.dispose();
                }
            }
        }
    }

    static void deleteSupplementaryFiles(CtfTmfTrace trace) {
        File suppDir = new File(TmfTraceManager.getSupplementaryFileDir(trace));
        for (File file : suppDir.listFiles()) {
            file.delete();
        }
    }

}