package com.beyond.generator.utils;

import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.gen.freemarker.MapperEntity;
import com.beyond.gen.freemarker.MapperXmlEntity;
import com.beyond.generator.Column;
import com.beyond.generator.StringUtil;
import com.beyond.generator.TypeConverter;
import com.beyond.generator.dom.Mapper;
import com.beyond.generator.dom.MapperLite;
import com.beyond.generator.ui.MsgDialog;
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.GenericAttributeValue;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.commons.lang3.ArrayUtils;
import org.codehaus.plexus.util.StringUtils;
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
import java.util.ArrayList;
import java.util.Collection;
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
                    mapperXmlEntity.setIdPropertyName(StringUtil.lineToHump(column.getColumnName()));
                    mapperXmlEntity.setIdJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                    break;
                }
            }
            for (Column column : columns) {
                String columnKey = column.getColumnKey();
                if (!org.apache.commons.lang3.StringUtils.equalsIgnoreCase("PRI", columnKey)) {
                    MapperXmlEntity.ColumnEntity columnEntity = new MapperXmlEntity.ColumnEntity();
                    columnEntity.setColumnName(column.getColumnName());
                    columnEntity.setPropertyName(StringUtil.lineToHump(column.getColumnName()));
                    columnEntity.setJdbcType(TypeConverter.toCommonJdbcType(column.getDataType()).toUpperCase());
                    mapperXmlEntity.getNormalColumns().add(columnEntity);
                }
            }
        } else {
            for (Column column : columns) {
                MapperXmlEntity.ColumnEntity columnEntity = new MapperXmlEntity.ColumnEntity();
                columnEntity.setColumnName(column.getColumnName());
                columnEntity.setPropertyName(StringUtil.lineToHump(column.getColumnName()));
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


    public static MapperLite findMapperXmlByName(Project project, String mapperFullName) {
        return findMapperXmlByName3(project, mapperFullName);
    }

    private static Mapper findMapperXmlByName1(Project project, String mapperFullName) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        List<DomFileElement<Mapper>> elements = DomService.getInstance().getFileElements(Mapper.class, project, scope);
        List<Mapper> mappers = elements.stream().map(DomFileElement::getRootElement).collect(Collectors.toList());
        for (Mapper mapper : mappers) {
            GenericAttributeValue<String> namespace = mapper.getNamespace();
            if (namespace.getValue() != null && org.apache.commons.lang3.StringUtils.equals(namespace.getStringValue(), mapperFullName)) {
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

    public static VirtualFile toVirtualFile(MapperLite mapper) {
        if (mapper == null) return null;
        return mapper.getVirtualFile();
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

    private static Map<String, MapperLite> mapperName2MapperMap = new HashMap<>();
    private static Map<String, Long> mapperName2MapperTimestampMap = new HashMap<>();

    public static MapperLite findMapperXmlByName3(Project project, String mapperFullName) {
        MapperLite xmlPath = mapperName2MapperMap.get(mapperFullName);
        Long time = mapperName2MapperTimestampMap.get(mapperFullName);
        if (xmlPath != null && System.currentTimeMillis() - time < 10000) {
            return xmlPath;
        }

        VirtualFile root = ProjectUtil.guessProjectDir(project);
        VirtualFile projectDir = root;
        if (root != null) {
            root = root.findFileByRelativePath("src/main");
        }
        if (root == null) root = projectDir;

        final MapperLite[] found = {null};
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
                            Element root = doc.getRootElement();
                            Attribute namespace = root.getAttribute("namespace");
                            String namespacestr = namespace == null ? null:  namespace.getValue();
                            if (org.apache.commons.lang3.StringUtils.equals(namespacestr, mapperFullName)) {
                                List<Element> selects = root.getChildren("select");
                                List<Element> resultMaps = root.getChildren("resultMap");
                                List<Element> sqls = root.getChildren("sql");
                                MapperLite mapper = new MapperLite();
                                mapper.setNamespace(namespacestr);
                                mapper.setSelectIds(selects.stream().map(x->x.getAttribute("id").getValue()).collect(Collectors.toList()));
                                mapper.setResultMapIds(resultMaps.stream().map(x->x.getAttribute("id").getValue()).collect(Collectors.toList()));
                                mapper.setResultMapId2TypeMap(resultMaps.stream().collect(Collectors.toMap(x->x.getAttribute("id").getValue(), x->x.getAttribute("type").getValue())));
                                mapper.setSqlIds(sqls.stream().map(x->x.getAttribute("id").getValue()).collect(Collectors.toList()));
                                mapper.setVirtualFile(file);
                                mapper.setText(text);
                                found[0] = mapper;
                                return false;
                            }
                        } catch (IOException | JDOMException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return super.visitFile(file);
            }
        });
        if (found[0] != null) {
            mapperName2MapperMap.put(mapperFullName, found[0]);
            mapperName2MapperTimestampMap.put(mapperFullName, System.currentTimeMillis());
        }
        return found[0];
    }


    public static List<MapperLite> findAllMapperXml(Project project){
        List<MapperLite> result = new ArrayList<>();

        VirtualFile root = ProjectUtil.guessProjectDir(project);
        VirtualFile projectDir = root;
        if (root != null) {
            root = root.findFileByRelativePath("src/main");
        }
        if (root == null) root = projectDir;

        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        SAXBuilder sb = new SAXBuilder();
        VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.isDirectory()) return super.visitFile(file);
                String extension = file.getExtension();
                if (org.apache.commons.lang3.StringUtils.equals(extension, "xml")) {
                    Document xmldoc = fileDocumentManager.getDocument(file);
                    if (xmldoc != null) {
                        final String text = xmldoc.getText();
                        try (StringReader stringReader = new StringReader(text)) {
                            org.jdom.Document doc = sb.build(stringReader);
                            Element root = doc.getRootElement();
                            Attribute namespace = root.getAttribute("namespace");
                            String namespacestr = namespace == null ? null:  namespace.getValue();
                            if (namespacestr != null) {
                                List<Element> selects = root.getChildren("select");
                                List<Element> inserts = root.getChildren("insert");
                                List<Element> updates = root.getChildren("update");
                                List<Element> resultMaps = root.getChildren("resultMap");
                                List<Element> sqls = root.getChildren("sql");
                                MapperLite mapper = new MapperLite();
                                mapper.setNamespace(namespacestr);
                                mapper.setSelectIds(selects.stream().map(x->x.getAttribute("id").getValue()).collect(Collectors.toList()));
                                mapper.setInsertIds(inserts.stream().map(x->x.getAttribute("id").getValue()).collect(Collectors.toList()));
                                mapper.setUpdateIds(updates.stream().map(x->x.getAttribute("id").getValue()).collect(Collectors.toList()));
                                mapper.setResultMapIds(resultMaps.stream().map(x->x.getAttribute("id").getValue()).collect(Collectors.toList()));
                                mapper.setSqlIds(sqls.stream().map(x->x.getAttribute("id").getValue()).collect(Collectors.toList()));
                                mapper.setVirtualFile(file);
                                mapper.setText(text);
                                result.add(mapper);
                                return false;
                            }
                        } catch (IOException | JDOMException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return super.visitFile(file);
            }
        });
        return result;
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
        if(StringUtils.isEmpty(s)) return;
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
        return psiClass != null && psiClass.isInterface() && (psiClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null || psiClass.getAnnotation("Mapper") != null);
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


    public static String getAnnotationSQL(PsiMethod psiMethod){
        PsiAnnotation select = psiMethod.getAnnotation("org.apache.ibatis.annotations.Select");
        if (select == null){
            select = psiMethod.getAnnotation("Select");
        }
        if (select != null) {
            return getValue(select);
        }
        PsiAnnotation update = psiMethod.getAnnotation("org.apache.ibatis.annotations.Update");
        if (update == null){
            update = psiMethod.getAnnotation("Update");
        }
        if (update != null) {
            return getValue(update);
        }
        PsiAnnotation insert = psiMethod.getAnnotation("org.apache.ibatis.annotations.Insert");
        if (insert == null){
            insert = psiMethod.getAnnotation("Insert");
        }
        if (insert != null) {
            return getValue(insert);
        }
        return null;

    }

    private static String getValue(PsiAnnotation select) {
        List<JvmAnnotationAttribute> attributes = select.getAttributes();
        for (JvmAnnotationAttribute attribute : attributes) {
            String attributeName = attribute.getAttributeName();
            if (org.apache.commons.lang3.StringUtils.isBlank(attributeName) || org.apache.commons.lang3.StringUtils.equalsIgnoreCase(attributeName,"value")){
                if ( attribute.getAttributeValue() instanceof JvmAnnotationConstantValue) {
                    return ((JvmAnnotationConstantValue) attribute.getAttributeValue()).getConstantValue().toString();
                }
                if ( attribute.getAttributeValue() instanceof JvmAnnotationArrayValue){
                    List<JvmAnnotationAttributeValue> values = ((JvmAnnotationArrayValue) attribute.getAttributeValue()).getValues();
                    List<String> ss = new ArrayList<>();
                    for (JvmAnnotationAttributeValue value : values) {
                        if (value instanceof JvmAnnotationConstantValue){
                            ss.add(((JvmAnnotationConstantValue) value).getConstantValue().toString());
                        }
                    }
                    return String.join(" ", ss);
                }
            }
        }
        return null;
    }

    public static Map<PsiMethod, String> getAllMapperMethod2Sql(Project project) throws JDOMException, IOException {
        Map<PsiMethod, String> result = new HashMap<>();
        List<MapperLite> allMapperXml = findAllMapperXml(project);
        for (MapperLite mapperLite : allMapperXml) {
            List<String> ids = new ArrayList<>();
            ids.addAll(mapperLite.getSelectIds());
            ids.addAll(mapperLite.getInsertIds());
            ids.addAll(mapperLite.getUpdateIds());
            Map<String, String> id2SqlMap = MybatisToSqlUtils.toSqls(mapperLite.getText(), ids);
            for (String id : id2SqlMap.keySet()) {
                PsiMethod method = PsiElementUtil.findMethodByFullClassNameAndName(project, mapperLite.getNamespace(), id);
                result.put(method, id2SqlMap.get(id));
            }
        }

        List<PsiClass> allMapperClass = findAllMapperClass(project);
        for (PsiClass mapperClass : allMapperClass) {
            @NotNull PsiMethod[] methods = mapperClass.getMethods();
            for (PsiMethod method : methods) {
                String annotationSQL = getAnnotationSQL(method);
                if (annotationSQL != null){
                    result.put(method, annotationSQL);
                }
            }
        }

        return result;
    }

    public static List<PsiClass> findAllMapperClass(Project project){
        List<PsiClass> result = new ArrayList<>();

        VirtualFile root = ProjectUtil.guessProjectDir(project);
        VirtualFile projectDir = root;
        if (root != null) {
            root = root.findFileByRelativePath("src/main");
        }
        if (root == null) root = projectDir;

        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.isDirectory()) return super.visitFile(file);
                String extension = file.getExtension();
                if (org.apache.commons.lang3.StringUtils.equals(extension, "java")) {
                    Document javaDocument = fileDocumentManager.getDocument(file);
                    if (javaDocument != null) {
                        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(javaDocument);
                        Collection<PsiClass> psiClasses = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass.class);
                        for (PsiClass psiClass : psiClasses) {
                            if (isMapperClass(psiClass)){
                                result.add(psiClass);
                            }
                        }
                    }
                }
                return super.visitFile(file);
            }
        });
        return result;
    }
}
