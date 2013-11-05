/*
 * Team : CODJO AM / OSI / SI / BO
 *
 * Copyright (c) 2001 CODJO Asset Management.
 */
package net.codjo.maven.mojo.delivery;
import net.codjo.maven.common.artifact.ArtifactDescriptor;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
/**
 *
 */
public class Include extends ArtifactDescriptor {
    private String output;
    private String file;
    private Artifact artifact;
    private Map properties = new HashMap();


    public String getOutput() {
        if (isLocalFile() && output == null) {
            output = new File(getFile()).getName();
        }
        return output;
    }


    public void setOutput(String output) {
        this.output = output;
    }


    public Artifact getArtifact() {
        return artifact;
    }


    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }


    public Map getProperties() {
        return properties;
    }


    public void setProperties(Map properties) {
        this.properties = properties;
    }


    public String getFile() {
        return file;
    }


    public void setFile(String file) {
        this.file = file;
    }


    public boolean isLocalFile() {
        return file != null;
    }


    public File getIncludedFile() {
        if (isLocalFile()) {
            return new File(getFile());
        }
        else {
            return getArtifact().getFile();
        }
    }
}
