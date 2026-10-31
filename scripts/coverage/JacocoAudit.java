import java.io.File;
import java.util.Arrays;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.tools.ExecFileLoader;

/** Fail instead of silently reporting mismatched JVM/device classes as zero hits. */
public final class JacocoAudit {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("class directories and execution data required");
        var data = new ExecFileLoader();
        for (String path : Arrays.copyOfRange(args, 1, args.length)) {
            File file = new File(path);
            if (!file.isFile() || file.length() == 0) throw new IllegalStateException("Missing/empty data: " + path);
            data.load(file);
        }
        if (data.getExecutionDataStore().getContents().isEmpty()) throw new IllegalStateException("No executed classes");
        var coverage = new CoverageBuilder();
        var analyzer = new Analyzer(data.getExecutionDataStore(), coverage);
        int classes = 0;
        for (String path : args[0].split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            File root = new File(path);
            if (root.exists()) classes += analyzer.analyzeAll(root);
        }
        if (classes == 0) throw new IllegalStateException("No production class files");
        if (!coverage.getNoMatchClasses().isEmpty()) {
            throw new IllegalStateException("JaCoCo class ID mismatch: " +
                coverage.getNoMatchClasses().stream().map(c -> c.getName()).toList());
        }
        System.out.println("JaCoCo class IDs verified; analyzed " + classes + " classes, including unexecuted classes.");
    }
}
