package equivalence;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.io.IOUtils;

import conversions.JFLAPToCFGAnalyzer;

public class GraderCFG {
    public static int grade(File input1, File input2, int limit, String analyzer) {
        int ret = -1;

        try {
            File i1 = File.createTempFile("cfganalyzer", ".cfg");
	    PrintStream output1 = new PrintStream(i1);
            File i2 = File.createTempFile("cfganalyzer", ".cfg");
	    PrintStream output2 = new PrintStream(i2);
            ProcessBuilder pb = new ProcessBuilder(analyzer, "--equivalence", "--maxbound", Integer.toString(limit), i1.getAbsolutePath(), i2.getAbsolutePath());

            JFLAPToCFGAnalyzer.convertGrammar(input1, output1);
            JFLAPToCFGAnalyzer.convertGrammar(input2, output2);
            pb.redirectErrorStream(true);

            Process p = pb.start();

            ret = p.waitFor();
            i1.delete();
            i2.delete();

	    if (ret != 2) {
		System.out.print(IOUtils.toString(p.getInputStream()));
	    }
        } catch (IOException e)  {
            e.printStackTrace();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        return ret;
    }


    public static void main(String[] args) {
        File input1 = new File(args[0]);
        File input2 = new File(args[1]);
        int limit = Integer.parseInt(args[2]);
        String analyzer = args[3];
	int ret = GraderCFG.grade(input1, input2, limit, analyzer);

	// Nasty workaround for what appears to be a CFGAnalyzer bug
	if (ret == 2) {
	    ret = GraderCFG.grade(input2, input1, limit, analyzer);
	}

        System.exit(ret);
    }
}
