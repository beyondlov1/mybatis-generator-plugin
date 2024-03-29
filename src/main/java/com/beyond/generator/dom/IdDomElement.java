package com.beyond.generator.dom;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.NameValue;
import com.intellij.util.xml.Required;

/**
 * The interface Id dom element.
 *
 * @author yanglin
 */
public interface IdDomElement extends DomElement {

    /**
     * Gets id.
     *
     * @return the id
     */
    @Required
    @NameValue
    @Attribute("id")
    GenericAttributeValue<String> getId();
}
