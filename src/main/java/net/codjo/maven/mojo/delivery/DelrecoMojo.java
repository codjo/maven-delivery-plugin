package net.codjo.maven.mojo.delivery;
import net.codjo.maven.common.ant.AntUtil;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.tools.ant.util.DateUtils;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
/**
 * @goal delreco
 * @aggregator
 */
public class DelrecoMojo extends AbstractMojo {
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     * @noinspection UnusedDeclaration
     */
    private MavenProject project;

    /**
     * @parameter expression="${workingDirectory}" default-value="${project.build.directory}/checkout"
     * @required
     * @noinspection UnusedDeclaration
     */
    private File workingDirectory;

    /**
     * @parameter expression="${assemblySearchPattern}" default-value="**\/*-assembly.zip"
     * @required
     * @noinspection UnusedDeclaration
     */
    private String assemblySearchPattern;

    /**
     * @parameter expression="${delrecoOutput}" default-value="${project.build.directory}/delreco-output"
     * @required
     * @noinspection UnusedDeclaration
     */
    private File delrecoOutput;

    /**
     * @parameter expression="${unzipDestinationFilename}" default-value="${project.build.directory}/delreco-tmp"
     * @required
     * @noinspection UnusedDeclaration
     */
    private File unzipDestination;

    /**
     * @parameter expression="${delrecoRootDirectory}" default-value="\\\\br-delreco\\DEPOT\\"
     * @required
     * @noinspection UnusedDeclaration
     */
    private String delrecoRootDirectory;

    /**
     * Maven's default input handler
     *
     * @component
     * @required
     * @readonly
     * @noinspection UnusedDeclaration
     */
    private InputHandler inputHandler;
    /**
     * @parameter expression="${settings}"
     * @required
     * @readonly
     */
    protected Settings settings;


    public void execute() throws MojoExecutionException, MojoFailureException {
        File[] assemblyFiles = getAssemblyFiles();
        unzipAssemblyFiles(assemblyFiles);
        File delrecoZip = createDelrecoZip();
        copyToTargetDirectory(delrecoZip);
    }


    private void unzipAssemblyFiles(File[] assemblyFiles) throws MojoFailureException {
        deleteDirectory(unzipDestination);
        AntUtil.unzipFiles(assemblyFiles, unzipDestination);
    }


    private File createDelrecoZip() throws MojoFailureException {
        deleteDirectory(delrecoOutput);
        delrecoOutput.mkdirs();
        File delrecoZipFile = new File(delrecoOutput, buildDelrecoZipFilename());
        AntUtil.zip(unzipDestination, delrecoZipFile);
        getLog().info("\nProduction du livrable DELRECO termine\n"
                      + "         livrable      : " + delrecoZipFile + "\n"
                      + "         zone de depot : " + delrecoRootDirectory + project.getName().toUpperCase()
                      + "\n");
        return delrecoZipFile;
    }


    private void copyToTargetDirectory(File delrecoZip) throws MojoExecutionException {
        String response = "yes";
        try {
            if (settings.isInteractiveMode()) {
                getLog().info("");
                getLog().info("Voulez-vous copier automatiquement le zip dans le depot ? (y/n)");
                response = inputHandler.readLine();
            }
            else {
                getLog().info("");
                getLog().info("Copie du zip dans le depot");
            }

            if ("y".equals(response.trim().toLowerCase()) || "yes".equals(response.trim().toLowerCase())) {
                AntUtil.copyFile(delrecoZip,
                                 new File(delrecoRootDirectory + project.getName().toUpperCase()));
                getLog().info("");
                getLog().info("\t\tCopie du fichier '" + delrecoZip.getName() + "' effectuee");
                getLog().info("");
            }
        }
        catch (IOException exception) {
            throw new MojoExecutionException("Exception lors la copie du zip", exception);
        }
    }


    private String buildDelrecoZipFilename() {
        String today = DateUtils.format(System.currentTimeMillis(), "yyyyMMdd");
        return project.getName() + "_" + today + "_" + retrieveStableVersion() + ".zip";
    }


    private String retrieveStableVersion() {
        String version = "VERSION";
        File pomFile = new File(workingDirectory, "pom.xml");

        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            Model model = reader.read(new FileReader(pomFile));
            version = model.getVersion();
        }
        catch (Exception e) {
            getLog().error(e);
        }

        return version;
    }


    private File[] getAssemblyFiles() {
        DirectoryScanner scanner = new DirectoryScanner();
        String[] includes = new String[]{assemblySearchPattern};
        scanner.setIncludes(includes);
        scanner.setBasedir(workingDirectory);
        scanner.setCaseSensitive(true);
        scanner.scan();

        String[] includedFiles = scanner.getIncludedFiles();
        File[] files = new File[includedFiles.length];
        for (int i = 0; i < includedFiles.length; i++) {
            String includedFile = includedFiles[i];
            files[i] = new File(scanner.getBasedir(), includedFile);
        }
        return files;
    }


    private void deleteDirectory(File directory) throws MojoFailureException {
        if (directory.exists()) {
            try {
                FileUtils.deleteDirectory(directory);
            }
            catch (IOException e) {
                throw new MojoFailureException(
                      "Unable to delete directory '" + directory.getAbsolutePath() + "'");
            }
        }
    }
}
