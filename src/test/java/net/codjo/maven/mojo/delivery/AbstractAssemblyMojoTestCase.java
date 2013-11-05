package net.codjo.maven.mojo.delivery;
import net.codjo.maven.common.mock.AgfMojoTestCase;
import net.codjo.maven.common.resources.FilteredManager;
import net.codjo.maven.common.test.FileUtil;
import net.codjo.test.common.FileComparator;
import net.codjo.test.common.fixture.CompositeFixture;
import net.codjo.test.common.fixture.DirectoryFixture;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.assembly.archive.phase.DependencySetAssemblyPhase;
import org.apache.maven.plugin.assembly.archive.phase.FileItemAssemblyPhase;
import org.apache.maven.plugin.assembly.archive.phase.FileSetAssemblyPhase;
import org.apache.maven.plugin.assembly.archive.phase.ModuleSetAssemblyPhase;
import org.apache.maven.plugin.assembly.archive.phase.RepositoryAssemblyPhase;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.FileScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.ant.taskdefs.condition.FilesMatch;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.archiver.manager.DefaultArchiverManager;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

public abstract class AbstractAssemblyMojoTestCase extends AgfMojoTestCase {
    protected static final String SRC_PATH = "src";
    protected static final String LOCAL_REPOSITORY_PATH = "./target/localRepository/";
    protected DirectoryFixture localRepository = new DirectoryFixture(LOCAL_REPOSITORY_PATH);

    private File basedirForTest;
    protected DirectoryFixture targetForTest;
    protected DirectoryFixture expandedZipDirectory;
    private CompositeFixture composite = new CompositeFixture(localRepository);
    protected AssemblyMojo mojo;
    protected static final boolean UNIX_FORMAT = true;
    protected static final boolean WINDOWS_FORMAT = false;
    protected static final String REMOTE_REPOSITORY = "src/test/resources/remoteRepository/";


    protected void simpleSetUp(String testName) throws Exception {
        setupEnvironment("/assembly/" + testName + "/pom.xml");

        mojo = (AssemblyMojo)lookupMojo("assembler");

        basedirForTest = new File("target/test-classes/assembly/" + testName);
        targetForTest = new DirectoryFixture(basedirForTest + "/target");
        expandedZipDirectory = new DirectoryFixture(targetForTest + "/zip-content");

        composite.addFixture(targetForTest);
        composite.addFixture(expandedZipDirectory);
        composite.doSetUp();

        mojo.setBasedir(basedirForTest);
        mojo.setBuildDirectory(targetForTest);
        mojo.setOutputDirectory(new File(targetForTest, "output"));
        mojo.setTempRoot(new File(targetForTest, "tmp"));

        getProject().setGroupId("mint");
        getProject().setArtifactId("mint-server");
        getProject().getModel().addProperty("logDir", "$MINT_LOG/java/");
        getProject().getModel().addProperty("serverMainClass", "net.codjo.mint.ServerMain");
        getProject().getBuild().setDirectory("./target");

        initializeAssemblyPhases();
    }


    protected void copyConfigToTarget() throws IOException {
        Resource config = new Resource();
        config.setDirectory(inBaseDir("src/config").getPath());
        config.setFiltering(true);
        FilteredManager filteredManager = new FilteredManager(getProject());
        filteredManager.copyResources(Collections.singletonList(config),
                                      inBaseDir("target/config").getPath());
    }


    protected DefaultArtifact createArtifact(String scopeCompile, String artifactId, String version) {
        DefaultArtifact artifact = new DefaultArtifact("net.codjo.agent",
                                                       artifactId,
                                                       VersionRange.createFromVersion(version),
                                                       scopeCompile,
                                                       "jar",
                                                       null,
                                                       new DefaultArtifactHandler(),
                                                       false);
        artifact.setFile(new File("src/test/resources/assembly", artifact.getArtifactId()
                                                                 + "-"
                                                                 + artifact.getVersion()
                                                                 + ".jar"));
        return artifact;
    }


    protected Dependency createDependency(String groupId,
                                          String artifactId,
                                          String classifier,
                                          String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setClassifier(classifier);
        dependency.setVersion(version);
        return dependency;
    }


    protected void createFile(File file, String fileContent) throws IOException {
        file.getParentFile().mkdirs();
        FileUtil.saveContent(file, fileContent);
    }


    protected File expand(File zipFile) {
        assertFileExists(zipFile);
        Expand expand = new Expand();
        expand.setProject(new Project());
        expand.setSrc(zipFile);
        expand.setDest(expandedZipDirectory);
        expand.execute();
        return expandedZipDirectory;
    }


    protected File zipFile(String zipFileName) {
        return new File(mojo.getOutputDirectory(), zipFileName);
    }


    protected File inExpanded(String relativeFilePath) {
        return new File(expandedZipDirectory, relativeFilePath);
    }


    protected File inBaseDir(String actual) {
        return new File(basedirForTest, actual);
    }


    protected void assertFiles(String[] assertions) throws IOException {
        for (int i = 0; i < assertions.length; i++) {
            String assertion = assertions[i];
            String[] parts = assertion.split("=");
            File expected = toFile(parts[0].trim());
            File actual = toFile(parts[1].trim());
            assertFileEquals(expected, actual);
        }
    }


    protected void assertFileEquals(File expected, File actual) throws IOException {
        assertFileExists(expected);
        assertFileExists(actual);
        assertTrue(toLabel(expected) + " = " + toLabel(actual),
                   new FileComparator("*").equals(expected, actual));
    }


    protected void assertFileDoesntExist(File file) {
        assertFalse("'" + file.getName() + "' should not exist", file.exists());
    }


    protected void assertSameBinaryFile(File expected, File actual) {
        assertFileExists(expected);
        assertFileExists(actual);
        FilesMatch filesMatch = new FilesMatch();
        filesMatch.setFile1(expected);
        filesMatch.setFile2(actual);

        assertTrue("Binary file should be identical", filesMatch.eval());
    }


    protected void assertContentOf(File zipFile, String[] expectedFiles) throws IOException {
        expand(zipFile);
        Arrays.sort(expectedFiles);
        FileScanner scanner = new DirectoryScanner();
        scanner.setBasedir(expandedZipDirectory);
        scanner.scan();
        String[] actualFiles = scanner.getIncludedFiles();
        Arrays.sort(actualFiles);

        assertEquals("Contenu du ZIP", toString(expectedFiles), toString(actualFiles));
        for (int i = 0; i < expectedFiles.length; i++) {
            String file = expectedFiles[i];
            if (file.contains("[")) {
                String formatAsString = file.substring(file.indexOf("[") + 1, file.indexOf("]"));
                checkFileFormat(toFormat(formatAsString), inExpanded(file.replaceAll("\\[.*]", "").trim()));
            }
        }
    }


    private boolean toFormat(String formatAsString) {
        if ("UNIX_FORMAT".equals(formatAsString)) {
            return UNIX_FORMAT;
        }
        if ("WINDOWS_FORMAT".equals(formatAsString)) {
            return WINDOWS_FORMAT;
        }
        throw new InternalError("format inconnu " + formatAsString);
    }


    private void checkFileFormat(boolean unixFormat, File expected) throws IOException {
        assertFileExists(expected);
        if (unixFormat) {
            assertTrue(expected.getName() + " should be in UNIX format",
                       FileUtil.loadContent(expected).indexOf("\r\n") < 0);
        }
        else {
            assertTrue(expected.getName() + " should be in WINDOWS format",
                       FileUtil.loadContent(expected).indexOf("\r\n") >= 0);
        }
    }


    private String toString(String[] files) {
        StringBuffer expected = new StringBuffer();
        for (int i = 0; i < files.length; i++) {
            String file = files[i];
            if (expected.length() != 0) {
                expected.append("\n");
            }
            expected.append(file.replaceAll("\\[.*]", "").replaceAll("\\\\", "/").trim());
        }
        return expected.toString();
    }


    private File toFile(String part) {
        if (part.startsWith("${basedir}")) {
            return inBaseDir(part.substring("${basedir}".length()));
        }
        if (part.startsWith("${zipdir}")) {
            return inExpanded(part.substring("${zipdir}".length()));
        }
        return new File(part);
    }


    private String toLabel(File file) {
        return "'" + file.getParentFile().getName() + "/" + file.getName() + "'";
    }


    private void assertFileExists(File file) {
        assertTrue(toLabel(file) + " should exists",
                   file.exists());
    }


    private void initializeAssemblyPhases() throws ContextException {
        // Ajout lors de la bascule vers 2.1

        DefaultArchiverManager archiverManager = new DefaultArchiverManager();
        DefaultContext context = new DefaultContext();
        context.put(PlexusConstants.PLEXUS_KEY, getContainer());
        archiverManager.contextualize(context);
        List phases = new ArrayList();

        phases.add(createFileItemAssemblyPhase());
        phases.add(new DependencySetAssemblyPhase());
        phases.add(new FileSetAssemblyPhase());
        phases.add(new RepositoryAssemblyPhase());
        phases.add(new ModuleSetAssemblyPhase());

        mojo.setAssemblyPhases(phases);
        mojo.setArchiverManager(archiverManager);
    }


    private FileItemAssemblyPhase createFileItemAssemblyPhase() {
        FileItemAssemblyPhase fileItemPhase = new FileItemAssemblyPhase();
        fileItemPhase.enableLogging(new ConsoleLogger(Logger.LEVEL_DEBUG, "FileItemAssemblyPhase"));
        return fileItemPhase;
    }


    protected void tearDown() throws Exception {
        composite.doTearDown();
        super.tearDown();
    }
}
