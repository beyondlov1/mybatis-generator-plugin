package com.beyond.generator.dom;


import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/**
 * @author beyond
 * @date 2023/02/16
 */
public class MapperLite {
    private VirtualFile virtualFile;
    private String getNamespace;
    private List<String> selectIds;
    private List<String> resultMapIds;
    private List<String> sqlIds;

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    public void setVirtualFile(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
    }

    public String getGetNamespace() {
        return getNamespace;
    }

    public void setGetNamespace(String getNamespace) {
        this.getNamespace = getNamespace;
    }

    public List<String> getSelectIds() {
        return selectIds;
    }

    public void setSelectIds(List<String> selectIds) {
        this.selectIds = selectIds;
    }

    public List<String> getResultMapIds() {
        return resultMapIds;
    }

    public void setResultMapIds(List<String> resultMapIds) {
        this.resultMapIds = resultMapIds;
    }

    public List<String> getSqlIds() {
        return sqlIds;
    }

    public void setSqlIds(List<String> sqlIds) {
        this.sqlIds = sqlIds;
    }
}
