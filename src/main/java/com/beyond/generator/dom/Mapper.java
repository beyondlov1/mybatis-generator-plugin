package com.beyond.generator.dom;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.NameValue;
import com.intellij.util.xml.Namespace;
import com.intellij.util.xml.Required;
import com.intellij.util.xml.SubTagList;
import org.jetbrains.annotations.NotNull;

import java.util.List;


/**
 *
 * Quote from 'MyBatisX' plugin:
 *
 */
public interface Mapper extends DomElement {


    /**
     * Gets namespace.
     *
     * @return the namespace
     */
    @Required
    @NameValue
    @NotNull
    @Attribute("namespace")
    GenericAttributeValue<String> getNamespace();


    @NotNull
    @SubTagList("select")
    List<IdDomElement> getSelects();


    @NotNull
    @SubTagList("resultMap")
    List<IdDomElement> getResultMaps();


    @NotNull
    @SubTagList("sql")
    List<IdDomElement> getSqls();

}
