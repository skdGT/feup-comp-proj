import org.junit.Before;
import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.JmmParserResult;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ParserTest {
    private final PrintStream realSystemOut = System.out;
    private static class NullOutputStream extends OutputStream {
        @Override
        public void write(int b){
        }
        @Override
        public void write(byte[] b){
        }
        @Override
        public void write(byte[] b, int off, int len){
        }
        public NullOutputStream(){
        }
    }

    private List<String> validFiles;
    private List<String> syntacticalErrorFiles;
    private List<String> semanticErrorFiles;

    @Before
    public void setup() {
        validFiles = new ArrayList<>();
        File dir = new File("test/fixtures/public/");
        String[] names = dir.list((dir1, name) -> name.endsWith(".jmm"));

        assert names != null;
        validFiles.addAll(Arrays.asList(names));

        syntacticalErrorFiles = new ArrayList<>();
        File dirSyn = new File("test/fixtures/public/fail/syntactical/");
        String[] synNames = dirSyn.list((dir1, name) -> name.endsWith(".jmm"));

        assert synNames != null;
        for (String synName : synNames)
            syntacticalErrorFiles.add("/fail/syntactical/" + synName);

        semanticErrorFiles = new ArrayList<>();
        File dirSem = new File("test/fixtures/public/fail/semantic/");
        String[] semNames = dirSem.list((dir1, name) -> name.endsWith(".jmm"));

        assert semNames != null;
        for (String semName : semNames)
            semanticErrorFiles.add("/fail/semantic/" + semName);
    }

    @Test
    public void unitTest() throws IOException {
        System.out.println("Unit Test");
        String code = TestUtils.getJmmCode("MonteCarloPi.jmm");

        JmmParserResult result = TestUtils.parse(code);



        System.out.println(result.getRootNode().toJson());

        // assertEquals("Program", TestUtils.parse(code).getRootNode().getKind());
    }

    @Test
    public void testParser() throws IOException {
        System.out.println("\nTesting Valid Files");
        for (String filename : this.validFiles) {
            System.out.print("Testing: " + filename);
            String code = TestUtils.getJmmCode(filename);
            System.setOut(new PrintStream(new NullOutputStream()));
            assertEquals("Program", TestUtils.parse(code).getRootNode().getKind());
            System.setOut(realSystemOut);
            System.out.print("  - PASSED\n");
        }
	}

	/*@Test
    public void testSyntacticalErrors() throws IOException {
        System.out.println("\nTesting Syntactical Errors");
        for (String filename : this.syntacticalErrorFiles) {
            System.out.print("Testing: " + filename);
            String code = TestUtils.getJmmCode(filename);
            System.setOut(new PrintStream(new NullOutputStream()));
            try {
                TestUtils.parse(code).getRootNode().getKind();
                System.setOut(realSystemOut);
                fail();
            } catch (Exception e) {
                System.setOut(realSystemOut);
                System.out.print("  - PASSED\n");
            }
        }
    }*/

    @Test
    public void testSemanticErrors() throws IOException {
        System.out.println("\nTesting Semantic Errors");
        for (String filename : this.semanticErrorFiles) {
            System.out.print("Testing: " + filename);
            String code = TestUtils.getJmmCode(filename);
            System.setOut(new PrintStream(new NullOutputStream()));
            assertEquals("Program", TestUtils.parse(code).getRootNode().getKind());
            System.setOut(realSystemOut);
            System.out.print("  - PASSED\n");
        }
    }
}
