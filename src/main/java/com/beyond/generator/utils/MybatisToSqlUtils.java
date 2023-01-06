package com.beyond.generator.utils;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Text;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author chenshipeng
 * @date 2022/11/10
 */
public class MybatisToSqlUtils {

    public static String toSql(File xmlFile, String id) throws JDOMException, IOException {
        try (FileReader reader = new FileReader(xmlFile)){
            return toSql(reader, id);
        }
    }

    public static String toSql(String xml, String id) throws JDOMException, IOException {
        try (StringReader stringReader = new StringReader(xml)){
            return toSql(stringReader, id);
        }
    }

    public static String toSql(Reader xmlReader, String id) throws JDOMException, IOException {
        SAXBuilder sb = new SAXBuilder();
        org.jdom.Document doc = sb.build(xmlReader);
        Element root = doc.getRootElement();
        Element node = (Element) XPath.selectSingleNode(root, String.format("//mapper/select[@id='%s']",id));
        if (node != null){
            List<Element> includes = XPath.selectNodes(node, "//include");
            List<Element> foreachs = XPath.selectNodes(node, "//foreach");
            for (Element include : includes) {
                Element parentElement = include.getParentElement();
                int i = parentElement.indexOf(include);
                parentElement.removeChild(include.getName());
                parentElement.addContent(i,new Text(getText(include, root)));
            }

            for (Element foreach : foreachs) {
                Element parentElement = foreach.getParentElement();
                int i = parentElement.indexOf(foreach);
                parentElement.removeChild(foreach.getName());
                parentElement.addContent(i,new Text(getText(foreach, root)));
            }
            return node.getText().replaceAll("\\#\\{.*?\\}", "?");
        }
        return null;
    }

    public static String toSql(String mybatisSqlXml) throws JDOMException, IOException {
        try (StringReader stringReader = new StringReader(mybatisSqlXml)){
            SAXBuilder sb = new SAXBuilder();
            org.jdom.Document doc = sb.build(stringReader);
            Element root = doc.getRootElement();
            Element node = root;
            if (node != null){
                List<Element> includes = XPath.selectNodes(node, "//include");
                List<Element> foreachs = XPath.selectNodes(node, "//foreach");
                for (Element include : includes) {
                    Element parentElement = include.getParentElement();
                    int i = parentElement.indexOf(include);
                    parentElement.removeChild(include.getName());
                    parentElement.addContent(i,new Text(getText(include, root)));
                }

                for (Element foreach : foreachs) {
                    Element parentElement = foreach.getParentElement();
                    int i = parentElement.indexOf(foreach);
                    parentElement.removeChild(foreach.getName());
                    parentElement.addContent(i,new Text(getText(foreach, root)));
                }
                return node.getText().replaceAll("\\#\\{.*?\\}", "?");
            }
            return null;
        }
    }


    private static String getText(Object o,Element root) throws JDOMException {
        StringBuilder s = new StringBuilder();
        if (o instanceof Element){
            Element element = (Element) o;
            if ("include".equals(element.getName())){
                Element sqlElement = (Element) XPath.selectSingleNode(root, String.format("//mapper/sql[@id='%s']", element.getAttributeValue("refid")));
                List children = sqlElement.getContent();
                for (Object child : children) {
                    s.append(getText(child, root));
                }
                return s.toString();
            }
            if ("foreach".equals(element.getName())){
                String open = element.getAttributeValue("open");
                String close = element.getAttributeValue("close");
                String separator = element.getAttributeValue("separator");

                List<String> foreachStrList = new ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    StringBuilder s1 = new StringBuilder();
                    for (Object o1 : element.getContent()) {
                        s1.append(getText(o1, root));
                    }
                    foreachStrList.add(s1.toString().trim());
                }
                String join = String.join(separator, foreachStrList);
                return open + join + close;
            }
            if ("if".equals(element.getName())){
                List children = element.getContent();
                for (Object child : children) {
                    s.append(getText(child, root));
                }
                return s.toString();
            }
            return ((Element) o).getTextTrim();
        }
        if (o instanceof Text){
            return ((Text) o).getTextTrim();
        }
        return "";
    }


    public static void main(String[] args) throws JDOMException, IOException {
        File file = new File("/home/beyond/work/project/order/src/main/resources/mapper/ProviderBundleDAOMapper.xml");
        String id = "getListByProviderIdsAndType";
        String sql = toSql(file, id);
        System.out.println(sql);
    }
}
