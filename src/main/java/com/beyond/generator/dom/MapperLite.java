package com.beyond.generator.dom;


import com.intellij.openapi.vfs.VirtualFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author beyond
 * @date 2023/02/16
 */
public class MapperLite {
    private VirtualFile virtualFile;
    private String namespace;
    private List<String> selectIds;
    private List<String>  insertIds;
    private List<String>  updateIds;
    private List<String> resultMapIds;
    private List<String> sqlIds;
    private String text;
    private Map<String,String> resultMapId2TypeMap = new HashMap<>();

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    public void setVirtualFile(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
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

    public List<String> getInsertIds() {
        return insertIds;
    }

    public void setInsertIds(List<String> insertIds) {
        this.insertIds = insertIds;
    }

    public List<String> getUpdateIds() {
        return updateIds;
    }

    public void setUpdateIds(List<String> updateIds) {
        this.updateIds = updateIds;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, String> getResultMapId2TypeMap() {
        return resultMapId2TypeMap;
    }

    public void setResultMapId2TypeMap(Map<String, String> resultMapId2TypeMap) {
        this.resultMapId2TypeMap = resultMapId2TypeMap;
    }
}
