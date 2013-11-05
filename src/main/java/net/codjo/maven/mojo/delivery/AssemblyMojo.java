/*
 * Team : CODJO AM / OSI / SI / BO
 *
 * Copyright (c) 2001 CODJO Asset Management.
 */
package net.codjo.maven.mojo.delivery;
import net.codjo.maven.common.artifact.ArtifactGetter;
import net.codjo.maven.common.resources.FilteredManager;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.wagon.PathUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.FileScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.util.GlobPatternMapper;
/**
 * @goal assembler
 * @requiresProject
 * @requiresDependencyResolution runtime
 */
public class AssemblyMojo extends AbstractSingleAssemblyMojo {
    /**
     * @parameter expression="${component.org.apache.maven.artifact.factory.ArtifactFactory}"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;
    /**
     * @parameter expression="${component.org.apache.maven.artifact.manager.WagonManager}"
     * @required
     * @readonly
     * @noinspection UnusedDeclaration
     */
    private WagonManager wagonManager;
    /**
     * @parameter expression="${component.org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager}"
     * @required
     * @readonly
     * @noinspection UnusedDeclaration
     */
    private RepositoryMetadataManager repositoryMetadataManager;
    /**
     * Liste des scripts shell.
     *
     * @parameter
     * @noinspection UnusedDeclaration
     */
    private Include[] includes;
    /**
     * Liste des excludes.
     *
     * @parameter
     * @noinspection UnusedDeclaration,MismatchedReadAndWriteOfArray
     */
    private Exclude[] excludes;
    /**
     * Directory containing the classes.
     *
     * @parameter default-value="${project.build.directory}"
     */
    protected File buildDirectory = new File("target");
    /**
     * Repertoire final contenant le livrable.
     *
     * @parameter
     */
    private String deliveryDirectory = null;


    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            executeImpl();
        }
        catch (IOException e) {
            throw new MojoExecutionException("Delivery error : " + e.getMessage(), e);
        }
        super.execute();
    }


    private void executeImpl() throws MojoFailureException, MojoExecutionException, IOException {
        if (getDescriptorId() != null) {
            setDescriptorRefs(new String[]{getDescriptorId()});
            setDescriptorId(null);
        }

        if (includes != null) {
            downloadIncludesFromRemoteToLocal();
            copyIncludesToTargetScript();
        }

        // Traitement de src/script
        File script = inBasedir("src/script");
        if (script.exists()) {
            copyAndFilter(script, inTarget("/script"));
        }

        // Traitement de src/config
        File config = inBasedir("/src/config");
        if (config.exists()) {
            createConfigTemplates(config);
        }

        // Traitement de src/binary
        File binary = inBasedir("src/binary");
        if (binary.exists()) {
            copy(binary, inTarget("binary"));
        }

        // Traitement spécifique
        if (assemblyFor("batch")) {
            writeDeliveryBatchFile(inTarget("/script/livraison-batch.txt"));
        }
        if (assemblyFor("client")) {
            renameJnlpToTemplate();
        }
    }


    protected void initializeProperties(String assemblyDescriptorId, Map context) {
        if (assemblyDescriptorId != null && deliveryDirectory == null) {

            if ("server".equalsIgnoreCase(assemblyDescriptorId)) {
                deliveryDirectory = "SERVEUR";
            }
            else {
                deliveryDirectory = assemblyDescriptorId.toUpperCase();
            }
        }
        context.put("deliveryDirectory", deliveryDirectory);
    }

    //--------------------------------------------------------------------------------------------------------
    // -- Assembly Client
    //--------------------------------------------------------------------------------------------------------


    private void renameJnlpToTemplate() throws IOException {
        File[] files = checkExistingFiles(buildDirectory, "/jnlp", "JNLP", ".jnlp");

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            renameToTemplate(file);
        }
    }


    private void renameToTemplate(File file) throws IOException {
        File targetTemplate = new File(file.getPath() + ".template");
        if (targetTemplate.exists() && !targetTemplate.delete()) {
            throw new IOException(
                  "Le fichier '" + targetTemplate + "' existe déjà et ne peut pas être supprimé");
        }
        else {
            boolean result = file.renameTo(targetTemplate);
            if (!result) {
                throw new IOException("Impossible de renommer le fichier '" + file + "' "
                                      + "en '" + targetTemplate + "'");
            }
        }
    }


    private File[] checkExistingFiles(File parent, String child, String fileType, String suffix)
          throws IOException {
        File propertiesDirectory = new File(parent, child);
        File[] files = propertiesDirectory.listFiles(new FileFilter(suffix));
        if (files == null) {
            throw new IOException("Aucun fichier " + fileType + " présent dans " + propertiesDirectory);
        }
        return files;
    }

    //--------------------------------------------------------------------------------------------------------
    // -- Assembly Batch
    //--------------------------------------------------------------------------------------------------------


    private void writeDeliveryBatchFile(File batchReleaseFile) throws IOException {
        batchReleaseFile.getParentFile().mkdirs();
        FileWriter fileWriter = new FileWriter(batchReleaseFile);
        try {
            FileScanner scanner = new DirectoryScanner();
            scanner.setBasedir(batchReleaseFile.getParentFile());
            scanner.setIncludes(new String[]{"**/*.*"});
            scanner.setExcludes(new String[]{batchReleaseFile.getName()});
            scanner.scan();
            String[] fileNames = scanner.getIncludedFiles();
            Arrays.sort(fileNames);

            for (int i = 0; i < fileNames.length; i++) {
                String fileName = fileNames[i];
                String relativePath = PathUtils.toRelative(batchReleaseFile.getParentFile(), fileName);
                fileWriter.write("./" + relativePath + System.getProperty("line.separator"));
            }
        }
        finally {
            fileWriter.close();
        }
    }

    //--------------------------------------------------------------------------------------------------------
    // -- Include Management
    //--------------------------------------------------------------------------------------------------------


    private void downloadIncludesFromRemoteToLocal() throws MojoFailureException {
        ArtifactGetter artifactGetter = new ArtifactGetter(artifactFactory,
                                                           getLocalRepository(),
                                                           getProject().getRemoteArtifactRepositories(),
                                                           wagonManager,
                                                           repositoryMetadataManager);
        for (int i = 0; i < includes.length; i++) {
            Include include = includes[i];
            if (include.isLocalFile()) {
                continue;
            }
            include.resolveIncludeVersion(getProject().getDependencyManagement());

            try {
                Artifact artifact = artifactGetter.getArtifact(include);
                include.setArtifact(artifact);
                getLog().info("Copy artifact : " + artifact.getArtifactId() + " " + artifact.getClassifier()
                              + " " + artifact.getType() + " from Remote to Local repository.");
            }
            catch (Exception e) {
                getLog().warn(e);
                throw new MojoFailureException(e, "Impossible de récupérer l'artifact.",
                                               "Impossible de récupérer l'artifact suivant : "
                                               + include.getGroupId() + " " + include.getArtifactId());
            }
        }
    }


    private void copyIncludesToTargetScript() throws IOException {
        for (int i = 0; i < includes.length; i++) {
            Include include = includes[i];
            File includedFile = include.getIncludedFile();
            if (include.isLocalFile() && !includedFile.exists()) {
                includedFile = inBasedir(includedFile.getPath());
            }

            Map properties = new HashMap();
            properties.put("preScript", "");
            properties.put("jvmArguments", "");
            properties.putAll(include.getProperties());

            FilteredManager filteredManager = new FilteredManager(getProject(), properties);
            filteredManager.setFilterToken("${", "}");
            filteredManager.copyFile(includedFile, inTarget("/script/" + include.getOutput()), true);
        }
    }

    //--------------------------------------------------------------------------------------------------------
    // -- Repertoire : binary
    //--------------------------------------------------------------------------------------------------------


    private void copy(File fromDir, File toDir) {
        Copy copy = new Copy();
        copy.setProject(new Project());

        FileSet set = new FileSet();
        set.setDir(fromDir);
        StringBuffer antExclude = new StringBuffer();
        if (excludes != null) {
            for (int i = 0; i < excludes.length; i++) {
                if (antExclude.length() != 0) {
                    antExclude.append(",");
                }
                antExclude.append(excludes[i].getFile());
            }
            set.setExcludes(antExclude.toString());
        }
        set.setIncludes("**/*.*");
        copy.addFileset(set);

        copy.setTodir(toDir);
        copy.execute();
    }

    //--------------------------------------------------------------------------------------------------------
    // -- Repertoire : config
    //--------------------------------------------------------------------------------------------------------


    private void createConfigTemplates(File sourceConfig) throws IOException {
        File tempConfig = inTarget("configtmp");

        // Preparation des templates
        copyAndFilter(sourceConfig, tempConfig);

        // Glob en *.template
        Copy copy = new Copy();
        copy.setProject(new Project());

        FileSet set = new FileSet();
        set.setDir(tempConfig);
        set.setIncludes("**/*.*");
        set.setExcludes("security.xml");
        copy.addFileset(set);

        GlobPatternMapper mapper = new GlobPatternMapper();
        mapper.setFrom("*");
        mapper.setTo("*.template");
        copy.add(mapper);

        copy.setTodir(new File(buildDirectory + "/config/"));
        copy.execute();
    }

    //--------------------------------------------------------------------------------------------------------
    // -- Utilitaires
    //--------------------------------------------------------------------------------------------------------


    private File inTarget(String relativePath) {
        return new File(buildDirectory, relativePath);
    }


    private File inBasedir(String relativePath) {
        return new File(getBasedir(), relativePath);
    }


    public void setBuildDirectory(File buildDirectory) {
        this.buildDirectory = buildDirectory;
    }


    public void setDeliveryDirectory(String deliveryDirectory) {
        this.deliveryDirectory = deliveryDirectory;
    }


    private void copyAndFilter(File fromDir, File toDir) throws IOException {
        FilteredManager filteredManager = new FilteredManager(getProject());
        filteredManager.setFilterToken("${", "}");

        Resource resource = new Resource();
        resource.setDirectory(fromDir.getPath());
        resource.setFiltering(true);
        if (excludes != null) {
            for (int i = 0; i < excludes.length; i++) {
                resource.addExclude(excludes[i].getFile());
            }
        }
        filteredManager.copyResources(Collections.singletonList(resource), toDir.getPath());
    }


    private boolean assemblyFor(String assemblyType) {
        return Arrays.asList(getDescriptorReferences()).contains(assemblyType);
    }


    private static class FileFilter implements FilenameFilter {
        private final String suffix;


        private FileFilter(String suffix) {
            this.suffix = suffix;
        }


        public boolean accept(File dir, String name) {
            return name.endsWith(suffix);
        }
    }
}
