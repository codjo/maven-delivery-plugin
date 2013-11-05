package net.codjo.maven.mojo.delivery;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.DefaultAssemblyArchiver;
import org.apache.maven.plugin.assembly.archive.phase.DependencySetAssemblyPhase;
import org.apache.maven.plugin.assembly.interpolation.AssemblyInterpolationException;
import org.apache.maven.plugin.assembly.interpolation.AssemblyInterpolator;
import org.apache.maven.plugin.assembly.io.AssemblyReadException;
import org.apache.maven.plugin.assembly.io.DefaultAssemblyReader;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.plugin.assembly.model.io.xpp3.AssemblyXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
/**
 * Adaptation entre le plugin maven-release-plugin et CODJO.
 */
public abstract class AbstractSingleAssemblyMojo extends CopyPastedClass {

    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     * @noinspection UnusedDeclaration
     */
    private MavenProject project;

    /**
     * Pour remplacer la step 'DependencySetAssemblyPhase'.
     *
     * @component
     */
    private ArchiverManager archiverManager;

    /**
     * Pour remplacer la step 'DependencySetAssemblyPhase'.
     *
     * @component role="org.apache.maven.plugin.assembly.archive.phase.AssemblyArchiverPhase"
     */
    private List assemblyPhases;


    /**
     * Create the binary distribution.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException
     *
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        setAssemblyReader(new AssemblyReaderAdapter());

        replaceMavenDependencyAssemblyPhase();
        setAssemblyArchiver(new DefaultAssemblyArchiver(archiverManager, assemblyPhases));

        super.execute();
    }


    private void replaceMavenDependencyAssemblyPhase() {
        for (int i = 0; i < assemblyPhases.size(); i++) {
            Object phase = assemblyPhases.get(i);
            if (phase instanceof DependencySetAssemblyPhase) {
                assemblyPhases.set(i, new SimpleDependencyAssemblyPhase(project));
            }
        }
    }


    public MavenProject getProject() {
        return project;
    }


    public void setArchiverManager(ArchiverManager archiverManager) {
        this.archiverManager = archiverManager;
    }


    public void setAssemblyPhases(List assemblyPhases) {
        this.assemblyPhases = assemblyPhases;
    }


    protected abstract void initializeProperties(String assemblyDescriptorId, Map context);


    Assembly getAssembly(String ref)
          throws InvalidAssemblerConfigurationException, AssemblyReadException {
        // Utiliser par les tests seulement
        return new AssemblyReaderAdapter().getAssemblyForDescriptorReference(ref, this);
    }


    private class AssemblyReaderAdapter extends DefaultAssemblyReader {
        public Assembly readAssembly(Reader reader,
                                     String locationDescription,
                                     AssemblerConfigurationSource configSource)
              throws AssemblyReadException, InvalidAssemblerConfigurationException {
            Assembly assembly;

            File basedir = configSource.getBasedir();
            MavenProject project = configSource.getProject();

            try {
                Map context = new HashMap(System.getProperties());

                context.put("basedir", basedir.getAbsolutePath());
                initializeProperties(locationDescription, context);

                AssemblyXpp3Reader assemblyXpp3Reader = new AssemblyXpp3Reader();
                assembly = assemblyXpp3Reader.read(reader);

                assembly = new AssemblyInterpolator().interpolate(assembly, project, context);
            }
            catch (IOException e) {
                throw new AssemblyReadException(
                      "Error reading descriptor at: " + locationDescription + ": " + e.getMessage(), e);
            }
            catch (XmlPullParserException e) {
                throw new AssemblyReadException(
                      "Error reading descriptor at: " + locationDescription + ": " + e.getMessage(), e);
            }
            catch (AssemblyInterpolationException e) {
                throw new AssemblyReadException(
                      "Error reading descriptor at: " + locationDescription + ": " + e.getMessage(), e);
            }
            finally {
                IOUtil.close(reader);
            }

            if (configSource.isSiteIncluded() || assembly.isIncludeSiteDirectory()) {
                includeSiteInAssembly(assembly, configSource);
            }

            mergeComponentsWithMainAssembly(assembly, configSource);

            return assembly;
        }
    }
}
