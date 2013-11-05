/*
 * Team : CODJO AM / OSI / SI / BO
 *
 * Copyright (c) 2001 CODJO Asset Management.
 */
package net.codjo.maven.mojo.delivery;
import net.codjo.util.file.FileUtil;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.plugin.assembly.model.FileSet;
/**
 *
 */
public class AssemblyMojoTest extends AbstractAssemblyMojoTestCase {

    public void test_getAssembly() throws Exception {
        setupEnvironment("/assembly/pom-assembly.xml");
        mojo = (AssemblyMojo)lookupMojo("assembler");

        Assembly assembly = mojo.getAssembly("client");
        assertEquals("CLIENT", ((FileSet)assembly.getFileSets().get(0)).getOutputDirectory());

        mojo.setDeliveryDirectory("CLIENT-ADMIN");

        assembly = mojo.getAssembly("client");
        assertEquals("CLIENT-ADMIN", ((FileSet)assembly.getFileSets().get(0)).getOutputDirectory());
    }


    public void test_server() throws Exception {
        simpleSetUp("server");

        // Utilisé pour server.sh
        getProject()
              .addToDependencyManagement(createDependency("net.codjo.agent",
                                                          "codjo-agent-bootstrap",
                                                          "server",
                                                          "0.2"));

        // les jar ?
        Set artifacts = new HashSet();
        artifacts.add(createArtifact(Artifact.SCOPE_COMPILE, "codjo-agent-bootstrap", "0.2"));
        artifacts.add(createArtifact(Artifact.SCOPE_TEST, "codjo-test", "1.0"));
        getProject().setArtifacts(artifacts);

        // Mock plugin config
        copyConfigToTarget();

        mojo.execute();

        // Check download : remote to local
        String artifactPath = "net/codjo/agent/codjo-agent-bootstrap"
                              + "/0.2/codjo-agent-bootstrap-0.2-server.sh";
        assertFileEquals(new File(REMOTE_REPOSITORY + artifactPath),
                         new File(LOCAL_REPOSITORY_PATH + artifactPath));

        // Check target
        assertFiles(new String[]{
              "${basedir}/expected/server.sh          = ${basedir}/target/script/server.sh",
              "${basedir}/expected/security.xml       = ${basedir}/target/config/security.xml"
        });

        // Check zip content
        assertContentOf(zipFile("/serveur.zip"), new String[]{
              "SERVEUR/codjo-agent-bootstrap-0.2.jar",
              "SERVEUR/server.sh [UNIX_FORMAT]",
              "SERVEUR/server-config.properties.template [UNIX_FORMAT]",
              "SERVEUR/specific.txt.template [UNIX_FORMAT]"
        });
    }


    public void test_server_withExclude() throws Exception {
        simpleSetUp("serverWithExclude");

        mojo.execute();

        // Check target
        assertFileEquals(inBaseDir("expected/local-server-file.ksh"),
                         inBaseDir("target/script/local-server-file.ksh"));
        assertFileDoesntExist(inBaseDir("target/script/local-excluded-file.ksh"));

        // Check zip content
        assertContentOf(zipFile("/serveur.zip"), new String[]{
              "SERVEUR/local-server-file.ksh [UNIX_FORMAT]"
        });
    }


    public void test_server_withBinary() throws Exception {
        simpleSetUp("serverWithBinary");

        mojo.execute();

        assertSameBinaryFile(inBaseDir("src/binary/busy.gif"), inBaseDir("target/binary/busy.gif"));

        assertContentOf(zipFile("/serveur.zip"), new String[]{
              "SERVEUR/busy.gif"
        });
    }


    public void test_server_withoutConfig() throws Exception {
        simpleSetUp("serverWithoutConfig");

        // Utilisé pour server.sh
        getProject()
              .addToDependencyManagement(createDependency("net.codjo.agent",
                                                          "codjo-agent-bootstrap",
                                                          "server",
                                                          "0.2"));

        mojo.execute();

        // Check target
        assertFiles(new String[]{
              "${basedir}/expected/server.sh             = ${basedir}/target/script/server.sh",
              "${basedir}/expected/local-server-file.ksh = ${basedir}/target/script/local-server-file.ksh"
        });

        // Check zip content
        assertContentOf(zipFile("/serveur.zip"), new String[]{
              "SERVEUR/server.sh [UNIX_FORMAT]",
              "SERVEUR/local-server-file.ksh [UNIX_FORMAT]"});
    }


    public void test_server_withWindowsCmd() throws Exception {
        simpleSetUp("serverWithWindowsCmd");

        mojo.execute();

        // Check zip content
        assertContentOf(zipFile("/serveur.zip"), new String[]{
              "SERVEUR/server.bat [WINDOWS_FORMAT]",
              "SERVEUR/server.cmd [WINDOWS_FORMAT]"});
    }


    public void test_server_onSnapshotVersion() throws Exception {
        simpleSetUp("serverOnSnapshot");

        // Utilisé pour server.sh
        getProject().addToDependencyManagement(
              createDependency("net.codjo.agent", "codjo-agent-bootstrap", "server", "0.1-SNAPSHOT"));

        mojo.execute();

        expand(zipFile("/serveur.zip"));

        String artifactPath = "net/codjo/agent/codjo-agent-bootstrap"
                              + "/0.1-SNAPSHOT/codjo-agent-bootstrap-0.1-20061109.160922-29-server.sh";
        assertFileEquals(new File(REMOTE_REPOSITORY + artifactPath),
                         new File(LOCAL_REPOSITORY_PATH + artifactPath));

        assertFiles(new String[]{"${basedir}/expected/server.sh = ${basedir}/target/script/server.sh"});
    }


    public void test_batch() throws Exception {
        simpleSetUp("batch");

        // Utilisé pour import.ksh & export.ksh
        getProject()
              .addToDependencyManagement(createDependency("net.codjo.agent",
                                                          "codjo-agent-bootstrap",
                                                          "import",
                                                          "0.2"));
        getProject()
              .addToDependencyManagement(createDependency("net.codjo.agent",
                                                          "codjo-agent-bootstrap",
                                                          "export",
                                                          "0.2"));

        // Mock plugin config
        copyConfigToTarget();

        mojo.execute();

        assertFiles(new String[]{
              "${basedir}/expected/export.ksh          = ${basedir}/target/script/export.ksh",
              "${basedir}/expected/import.ksh          = ${basedir}/target/script/import.ksh",
              "${basedir}/expected/livraison-batch.txt = ${basedir}/target/script/livraison-batch.txt"
        });

        assertContentOf(zipFile("/batch.zip"), new String[]{
              "BATCH/livraison-batch.txt [WINDOWS_FORMAT]",
              "BATCH/export.ksh          [UNIX_FORMAT]",
              "BATCH/import.ksh          [UNIX_FORMAT]",
              "BATCH/batch-config.properties.template"
        });
    }


    public void test_batch_usingDefaultProperties() throws Exception {
        simpleSetUp("batchUseDefaultProperties");

        // Utilisé pour import.ksh & export.ksh
        getProject()
              .addToDependencyManagement(createDependency("net.codjo.agent",
                                                          "codjo-agent-bootstrap",
                                                          "export",
                                                          "0.2"));

        mojo.execute();

        assertFiles(new String[]{
              "${basedir}/expected/export.ksh          = ${basedir}/target/script/export.ksh",
              "${basedir}/expected/livraison-batch.txt = ${basedir}/target/script/livraison-batch.txt"
        });
    }


    public void test_batch_withoutConfig() throws Exception {
        simpleSetUp("batchWithoutConfig");

        // Utilisé pour server.sh
        getProject()
              .addToDependencyManagement(createDependency("net.codjo.agent",
                                                          "codjo-agent-bootstrap",
                                                          "import",
                                                          "0.2"));

        mojo.execute();

        assertFiles(new String[]{
              "${basedir}/expected/local-batch.ksh     = ${basedir}/target/script/local-batch.ksh",
              "${basedir}/expected/import.ksh          = ${basedir}/target/script/import.ksh",
              "${basedir}/expected/livraison-batch.txt = ${basedir}/target/script/livraison-batch.txt"
        });

        // Check zip content
        assertContentOf(zipFile("/batch.zip"), new String[]{
              "BATCH/livraison-batch.txt [WINDOWS_FORMAT]",
              "BATCH/local-batch.ksh     [UNIX_FORMAT]",
              "BATCH/import.ksh          [UNIX_FORMAT]"
        });
    }


    public void test_batch_withLocalWindowsCommandFile() throws Exception {
        simpleSetUp("batchWithWindowsCmd");

        mojo.execute();

        assertFiles(new String[]{
              "${basedir}/src/script/local-batch.cmd      = ${basedir}/target/script/local-batch.cmd",
              "${basedir}/src/script/local-batch.bat      = ${basedir}/target/script/local-batch.bat",
              "${basedir}/expected/livraison-batch.txt    = ${basedir}/target/script/livraison-batch.txt"
        });

        assertContentOf(zipFile("/batch.zip"), new String[]{
              "BATCH/livraison-batch.txt [WINDOWS_FORMAT]",
              "BATCH/local-batch.cmd     [WINDOWS_FORMAT]",
              "BATCH/local-batch.bat     [WINDOWS_FORMAT]"
        });
    }


    public void test_batch_multiProperties() throws Exception {
        simpleSetUp("batchWithMultiProperties");
        getProject().getProperties().setProperty("from-propertyTag", "une valeur");

        // Mock plugin config
        copyConfigToTarget();

        mojo.execute();

        assertContentOf(zipFile("/batch.zip"), new String[]{
              "BATCH/livraison-batch.txt                ",
              "BATCH/aNewConfig.bat.template            [UNIX_FORMAT]",
              "BATCH/batch-config.properties.template   [UNIX_FORMAT]",
              "BATCH/another-config.properties.template [UNIX_FORMAT]"
        });

        assertFiles(new String[]{
              "${basedir}/expected/batch-config.properties.template   = ${zipdir}/BATCH/batch-config.properties.template",
              "${basedir}/expected/aNewConfig.bat.template            = ${zipdir}/BATCH/aNewConfig.bat.template",
              "${basedir}/expected/another-config.properties.template = ${zipdir}/BATCH/another-config.properties.template",
        });
    }


    public void test_batch_withBinary() throws Exception {
        simpleSetUp("batchWithBinary");

        mojo.execute();

        assertContentOf(zipFile("/batch.zip"), new String[]{
              "BATCH/livraison-batch.txt",
              "BATCH/busy.gif           ",
        });

        assertSameBinaryFile(inBaseDir("src/binary/busy.gif"), inExpanded("BATCH/busy.gif"));
    }


    public void test_batch_withExclude() throws Exception {
        simpleSetUp("batchWithExclude");

        // Mock plugin config
        copyConfigToTarget();

        mojo.execute();

        assertContentOf(zipFile("/batch.zip"), new String[]{
              "BATCH/livraison-batch.txt                ",
              "BATCH/import.sh                          ",
              "BATCH/batch-config.properties.template   [UNIX_FORMAT]"
        });

        assertFiles(new String[]{
              "${basedir}/expected/batch-config.properties.template = ${zipdir}/BATCH/batch-config.properties.template",
              "${basedir}/expected/livraison-batch.txt              = ${zipdir}/BATCH/livraison-batch.txt"
        });
    }


    public void test_batch_withSubDirectories() throws Exception {
        simpleSetUp("batchWithSubDirectories");

        mojo.execute();

        assertFiles(new String[]{
              "${basedir}/expected/livraison-batch.txt = ${basedir}/target/script/livraison-batch.txt"});

        assertContentOf(zipFile("/batch.zip"), new String[]{
              "BATCH/livraison-batch.txt",
              "BATCH/unix/unixBatch.ksh",
              "BATCH/windows/windowsBatch.bat",
              "BATCH/windows/windowsBatch.cmd",
        });
    }


    public void test_client() throws Exception {
        simpleSetUp("client");
        createFile(inBaseDir("target/jnlp/specific.jnlp"), "");

        mojo.execute();

        assertContentOf(zipFile("/client.zip"), new String[]{"CLIENT/specific.jnlp.template"});
    }


    public void test_client_overwriteJnlpToTemplate() throws Exception {
        simpleSetUp("client");

        createFile(inBaseDir("./target/jnlp/specific.jnlp"), "new");
        createFile(inBaseDir("./target/jnlp/specific.jnlp.template"), "old");

        mojo.execute();

        assertContentOf(zipFile("/client.zip"), new String[]{"CLIENT/specific.jnlp.template"});
        assertEquals("new", FileUtil.loadContent(inExpanded("CLIENT/specific.jnlp.template")));
    }


    public void test_client_withBinary() throws Exception {
        simpleSetUp("clientWithBinary");

        createFile(inBaseDir("./target/jnlp/specific.jnlp"), "");

        mojo.execute();

        assertContentOf(zipFile("/client.zip"), new String[]{
              "CLIENT/specific.jnlp.template",
              "CLIENT/busy.gif"});
    }


    public void test_sql() throws Exception {
        simpleSetUp("sql");

        createFile(inBaseDir("./target/sql/table/DATAGEN.tab"), "create table...");

        mojo.execute();

        assertContentOf(zipFile("/sql.zip"), new String[]{
              "SQL/livraison-sql.txt",
              "SQL/table/AP_LOG.tab",
              "SQL/table/DATAGEN.tab"
        });
    }


    public void test_web() throws Exception {
        simpleSetUp("web");

        // Utilisé pour import.ksh & export.ksh
        getProject()
              .addToDependencyManagement(createDependency("net.codjo.agent",
                                                          "codjo-agent-bootstrap",
                                                          "import",
                                                          "0.2"));

        // Mock plugin config
        copyConfigToTarget();

        mojo.execute();

        assertFiles(new String[]{"${basedir}/expected/import.ksh = ${basedir}/target/script/import.ksh"
        });

        assertContentOf(zipFile("/web.zip"), new String[]{
              "WEB/web-config.properties.template",
              "WEB/import.ksh"
        });
    }


    public void test_web_withBinary() throws Exception {
        simpleSetUp("webWithBinary");

        mojo.execute();

        assertSameBinaryFile(inBaseDir("src/binary/busy.gif"), inBaseDir("target/binary/busy.gif"));

        assertContentOf(zipFile("/web.zip"), new String[]{"WEB/busy.gif"});
    }


    public void test_web_withExclude() throws Exception {
        simpleSetUp("webWithExclude");

        mojo.execute();

        assertContentOf(zipFile("/web.zip"), new String[]{
              "WEB/web-config.properties.template   [UNIX_FORMAT]"
        });
    }
}
