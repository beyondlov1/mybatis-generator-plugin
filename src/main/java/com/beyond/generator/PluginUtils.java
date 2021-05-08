package com.beyond.generator;


import com.intellij.openapi.project.Project;

/**
 * @author chenshipeng
 * @date 2021/02/23
 */
public class PluginUtils {

    public static String getProjectJavaPath(Project project){
        if (project == null){
            throw new RuntimeException("project is null");
        }
        return PathUtils.concat(getProjectSrcPath(project), "main","java");
    }

    public static String getPomPath(Project project){
        if (project == null){
            throw new RuntimeException("project is null");
        }

        return PathUtils.concat(project.getBasePath(), "pom.xml");
    }

    public static String getProjectSrcPath(Project project) {
        if (project == null){
            throw new RuntimeException("project is null");
        }
        return PathUtils.concat(project.getBasePath(), "src");
    }

}
