package com.beyond.generator.dom;

import com.intellij.util.xml.DomFileDescription;

/**
 *
 * Quote from 'MyBatisX' plugin:
 *
 */
public class MapperDescription extends DomFileDescription<Mapper> {

    /**
     * Instantiates a new Mapper description.
     */
    public MapperDescription() {
        super(Mapper.class, "mapper");
    }

    @Override
    protected void initializeFileDescription() {
        registerNamespacePolicy("MybatisXml", "http://mybatis.org/dtd/mybatis-3-mapper.dtd");
    }
}
