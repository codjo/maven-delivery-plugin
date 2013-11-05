package net.codjo.maven.mojo.delivery;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugin.assembly.archive.phase.AssemblyArchiverPhase;
import org.apache.maven.plugin.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.plugin.assembly.model.DependencySet;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
/**
 * Pour remplacer la phase (DependencySetAssemblyPhase).
 *
 * @see org.apache.maven.plugin.assembly.archive.phase.DependencySetAssemblyPhase
 */
class SimpleDependencyAssemblyPhase implements AssemblyArchiverPhase {
    public static final String JAR_TYPE = "jar";
    private MavenProject project;


    SimpleDependencyAssemblyPhase(MavenProject project) {
        this.project = project;
    }


    public void execute(Assembly assembly, Archiver archiver, AssemblerConfigurationSource configSource)
          throws ArchiveCreationException,
                 AssemblyFormattingException,
                 InvalidAssemblerConfigurationException {

        List list = assembly.getDependencySets();
        if (list == null || list.isEmpty()) {
            return;
        }
        DependencySet dependencySet = getFirstDependencySet(list);

        try {
            addDependenciesInto(archiver, dependencySet.getOutputDirectory());
        }
        catch (ArchiverException e) {
            throw new ArchiveCreationException("Impossible de copier les dependances", e);
        }
    }


    private List addDependenciesInto(Archiver archiver, String output) throws ArchiverException {
        List dependencies = new ArrayList();

        Collection artifacts = project.getArtifacts();
        for (Iterator it = artifacts.iterator(); it.hasNext();) {
            Artifact artifact = (Artifact)it.next();
            addIf(archiver, artifact, output);
        }

        return dependencies;
    }


    private void addIf(Archiver archiver, Artifact artifact, String output) throws ArchiverException {
        if (artifact == null
            || Artifact.SCOPE_PROVIDED.equals(artifact.getScope())
            || Artifact.SCOPE_TEST.equals(artifact.getScope())
            || !JAR_TYPE.equals(artifact.getType())) {
            return;
        }

        File source = artifact.getFile();
        archiver.addFile(source, output + "/" + source.getName());
    }


    private DependencySet getFirstDependencySet(List list) throws InvalidAssemblerConfigurationException {
        if (list.size() > 1) {
            throw new InvalidAssemblerConfigurationException(
                  "On ne gere pas plusieurs balises <dependencySet>");
        }
        return (DependencySet)list.get(0);
    }
}
