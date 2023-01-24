package com.beyond.generator.utils;

import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.gen.freemarker.MapperEntity;
import com.beyond.gen.freemarker.MapperXmlEntity;
import com.beyond.generator.Column;
import com.beyond.generator.StringUtils;
import com.beyond.generator.TypeConverter;
import com.beyond.generator.dom.Mapper;
import com.beyond.generator.ui.MsgDialog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.GenericAttributeValue;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.commons.lang.ArrayUtils;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author chenshipeng
 * @date 2022/11/23
 */
public class MapperUtil {
    public static MapperXmlEntity createMapperXmlEntity(JavaEntity javaEntity, MapperEntity mapperEntity, List<Column> columns) {
        return createMapperXmlEntity(javaEntity.getPackageName() + "." + javaEntity.getClassName(), mapperEntity.getPackageName() + "." + mapperEntity.getMapperName(), columns);
    }

    public static MapperXmlEntity createMapperXmlEntity(String entityFullName, String mapperFullName, List<Column> columns) {
        MapperXmlEntity mapperXmlEntity = new MapperXmlEntity();
        mapperXmlEntity.setEntityClassFullName(entityFullName);
        mapperXmlEntity.setMapperClassFullName(mapperFullName);
        int primaryColumnCount = 0;
        for (Column column : columns) {
            String columnKey = column.getColumnKey();
            if (org.apache.commons.lang3.StringUtils.equalsIgnoreCase("PRI", columnKey)) {
                primaryColumnCount++;
            }
        }
        if (primaryColumnCount == 1) {
            for (Column column : columns) {
                String columnKey = column.getColumnKey();
                if (org.apache.commons.lang3.StringUtils.equalsIgnoreCase("PRI", columnKey)) {
                    mapperXmlEntity.setIdColumnName(column.getColumnName());
                    mapperXmlEntity.setIdPropertyName(StringUtils.lineToHump(column.getColumnName()));
                    mapperXmlEntity.setIdJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                    break;
                }
            }
            for (Column column : columns) {
                String columnKey = column.getColumnKey();
                if (!org.apache.commons.lang3.StringUtils.equalsIgnoreCase("PRI", columnKey)) {
                    MapperXmlEntity.ColumnEntity columnEntity = new MapperXmlEntity.ColumnEntity();
                    columnEntity.setColumnName(column.getColumnName());
                    columnEntity.setPropertyName(StringUtils.lineToHump(column.getColumnName()));
                    columnEntity.setJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                    mapperXmlEntity.getNormalColumns().add(columnEntity);
                }
            }
        } else {
            for (Column column : columns) {
                MapperXmlEntity.ColumnEntity columnEntity = new MapperXmlEntity.ColumnEntity();
                columnEntity.setColumnName(column.getColumnName());
                columnEntity.setPropertyName(StringUtils.lineToHump(column.getColumnName()));
                columnEntity.setJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                mapperXmlEntity.getNormalColumns().add(columnEntity);
            }
        }

        return mapperXmlEntity;
    }

    public static MapperXmlEntity createMapperXmlResultMapEntity(String entityFullName, List<Column> columns) {
        return createMapperXmlEntity(entityFullName, null, columns);
    }

    public static MapperXmlEntity createMapperXmlColumnListEntity(List<Column> columns) {
        return createMapperXmlEntity((String) null, (String) null, columns);
    }

    public static Mapper findMapperXmlByName(Project project, String mapperFullName) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        List<DomFileElement<Mapper>> elements = DomService.getInstance().getFileElements(Mapper.class, project, scope);
        List<Mapper> mappers = elements.stream().map(DomFileElement::getRootElement).collect(Collectors.toList());
        for (Mapper mapper : mappers) {
            GenericAttributeValue<String> namespace = mapper.getNamespace();
            if (namespace.getValue() != null && org.apache.commons.lang3.StringUtils.equals(namespace.getValue(), mapperFullName)) {
                return mapper;
            }
        }
        return null;
    }

    public static VirtualFile toVirtualFile(Mapper mapper) {
        if (mapper != null && mapper.exists() && mapper.getXmlElement() != null && mapper.getXmlElement().getContainingFile() != null) {
            return mapper.getXmlElement().getContainingFile().getVirtualFile();
        } else {
            return null;
        }
    }

    private static Map<String, VirtualFile> mapper2xmlMap = new HashMap<>();

    public static VirtualFile findMapperXmlByName2(Project project, String mapperFullName) {
        VirtualFile root = ProjectUtil.guessProjectDir(project);
        VirtualFile xmlPath = mapper2xmlMap.get(mapperFullName);
        if (xmlPath != null) {
            if (xmlPath.exists()) {
                return xmlPath;
            } else {
                mapper2xmlMap.remove(mapperFullName);
            }
        }

        VirtualFile projectDir = root;
        if (root != null) {
            root = root.findFileByRelativePath("src/main");
        }
        if (root == null) root = projectDir;

        final VirtualFile[] found = {null};
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        SAXBuilder sb = new SAXBuilder();
        VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.isDirectory()) return super.visitFile(file);
                if (found[0] != null) return false;
                String extension = file.getExtension();
                if (org.apache.commons.lang3.StringUtils.equals(extension, "xml")) {
                    Document xmldoc = fileDocumentManager.getDocument(file);
                    if (xmldoc != null) {
                        final String text = xmldoc.getText();
                        if (!text.contains(mapperFullName)) return false;
                        try (StringReader stringReader = new StringReader(text)) {
                            org.jdom.Document doc = sb.build(stringReader);
                            Attribute namespaceText = (Attribute) XPath.selectSingleNode(doc.getRootElement(), "//mapper/@namespace");
                            if (namespaceText != null) {
                                String namespace = namespaceText.getValue();
                                if (org.apache.commons.lang3.StringUtils.equals(namespace, mapperFullName)) {
                                    found[0] = file;
                                    return false;
                                }
                            }
                        } catch (IOException | JDOMException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return super.visitFile(file);
            }
        });
        if (found[0] != null && found[0].exists()) {
            mapper2xmlMap.put(mapperFullName, found[0]);
        }
        return found[0];
    }


    @Nullable
    public static Element getElementByNameAndAttr(Element root, String xpath, String attr, String attrTarget) throws JDOMException {
        List<Element> resultMapElements = (List<Element>) XPath.selectNodes(root, xpath);
        Element resultMapElement = null;
        for (Element element : resultMapElements) {
            String attrVal = element.getAttributeValue(attr);
            if (org.apache.commons.lang3.StringUtils.equals(attrVal, attrTarget)) {
                resultMapElement = element;
                break;
            }
        }
        return resultMapElement;
    }

    public static void msg(@NotNull Project project, String s) {
//        MsgDialog msgDialog = new MsgDialog(project, s);
//        msgDialog.show();

//        JOptionPane.showMessageDialog(null, s, "MyBatis-Generator", JOptionPane.INFORMATION_MESSAGE);
        Notifications.Bus.notify(new Notification("MyBatis-Generator", "MyBatis-Generator", s, NotificationType.INFORMATION));
    }

    public static void msgE(@NotNull Project project, String s) {
        Notifications.Bus.notify(new Notification("MyBatis-Generator", "MyBatis-Generator", s, NotificationType.ERROR));
    }

    public static void msgDialog(@NotNull Project project, String s) {
        MsgDialog msgDialog = new MsgDialog(project, s);
        msgDialog.show();
    }


    public static String getPrevWord(Document document, Editor editor) {
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();
        if (selectionEnd == 0) return null;
        int len = 1;
        while (len < 50) {
            if (selectionEnd - len < 0) {
                len--;
                break;
            }
            String text = document.getText(TextRange.create(selectionEnd - len, selectionEnd));
            if (text.startsWith(" ") || text.startsWith("\n") || text.startsWith("\t") || text.startsWith(".")) {
                return text.substring(1);
            }
            if (text.startsWith("\r\n")) {
                return text.substring(2);
            }
            len++;
        }
        return document.getText(TextRange.create(selectionEnd - len, selectionEnd));
    }

    public static boolean testConnection(String jdbcUrl1, String username1, String password1) {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(jdbcUrl1);
        dataSource.setUser(username1);
        dataSource.setPassword(password1);
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(dataSource);

        String sql = "select 1";
        final String result = jdbcTemplate.queryForObject(sql, String.class);
        return "1".equals(result);
    }


    public static boolean isMapperClass(PsiClass psiClass) {
        return psiClass != null && psiClass.isInterface() && psiClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null;
    }


    public static String completeFullEntityName(@NotNull Project project, PsiDocumentManager psiDocumentManager, String entityName, PsiDocComment docComment) {
        PerformanceUtil.mark("completeFullEntityName1");
        if (!entityName.contains(".")) {
            @NotNull PsiClass[] entityClasses = PsiShortNamesCache.getInstance(project).getClassesByName(entityName, GlobalSearchScope.allScope(project));
            PerformanceUtil.mark("completeFullEntityName3");
            if (!ArrayUtils.isEmpty(entityClasses)) {
                PsiClass entityClass = entityClasses[0];
                Document document = psiDocumentManager.getDocument(docComment.getContainingFile());
                PsiDocumentUtils.commitAndSaveDocument(PsiDocumentManager.getInstance(project), document);
                PsiDocTag entity = docComment.findTagByName("entity");
                if (entity != null) entity.delete();
                PsiJavaParserFacadeImpl psiJavaParserFacade = new PsiJavaParserFacadeImpl(project);
                PsiDocTag docTag = psiJavaParserFacade.createDocTagFromText("@entity " + entityClass.getQualifiedName());
                docComment.add(docTag);
                PsiDocumentUtils.commitAndSaveDocument(PsiDocumentManager.getInstance(project), document);
                entityName = entityClass.getQualifiedName();
            }
        }
        PerformanceUtil.mark("completeFullEntityName2");
        return entityName;
    }


}
