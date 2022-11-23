package com.beyond.generator.action;

import com.beyond.gen.freemarker.FragmentGenUtils;
import com.beyond.generator.Column;
import com.beyond.generator.ui.JdbcForm;
import com.beyond.generator.ui.MsgDialog;
import com.beyond.generator.utils.MapperUtil;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatternUtil;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.beyond.generator.utils.PropertyUtil.*;
import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * @author chenshipeng
 * @date 2022/11/08
 */
public class GenerateMybatisFragmentAction extends PsiElementBaseIntentionAction {
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);


        PsiFile containingFile = element.getContainingFile();
        Document document = psiDocumentManager.getDocument(containingFile);
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);

        if (containingClass.isInterface()) {
            String methodName = getPrevWord(document, editor);
            if (StringUtils.isBlank(methodName)) return;

            gen(project, editor, psiDocumentManager, document, containingClass, methodName);
        } else {
            PsiClass psiClass;
            try {
                String methodName = element.getPrevSibling().getFirstChild().getLastChild().getText();
                psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild().getFirstChild()).resolve()).getType()).resolve();
                if (psiClass != null && psiClass.isInterface() && psiClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null) {
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null) {
                        gen2(project, psiDocumentManager, classDocument, psiClass, methodName, classDocument.getText().lastIndexOf("}"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void gen2(@NotNull Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, int start) {
        String entityName = null;
        PsiDocComment docComment = containingClass.getDocComment();
        if (docComment != null) {
            PsiDocTag tag = docComment.findTagByName("entity");
            if (tag != null) {
                PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement != null) {
                    entityName = valueElement.getText();
                }
            }
        }
        if (entityName == null) {
            MsgDialog msgDialog = new MsgDialog(project, "please add '/** @entity entityName */' in doc comment.");
            msgDialog.show();
            return;
        }

        String fullEntityName = null;
        if (entityName.contains(".")) {
            fullEntityName = entityName;
            entityName = StringUtils.substringAfterLast(entityName, ".");
        }


        @NotNull PsiMethod[] allMethods = containingClass.getAllMethods();
        for (PsiMethod allMethod : allMethods) {
            if (StringUtils.equals(allMethod.getName(), methodName)){
                MsgDialog msgDialog = new MsgDialog(project, "method exists.");
                msgDialog.show();
                return;
            }
        }

        boolean isContinue = genMapperXmlFragment(project, psiDocumentManager, containingClass, docComment, methodName);
        if (isContinue){
            isContinue = genMapperFragment2(project, psiDocumentManager, document, containingClass, methodName, start, entityName, fullEntityName);
            if (isContinue){
                MsgDialog msgDialog = new MsgDialog(project, "Success!");
                msgDialog.show();
            }
        }
    }

    private boolean genMapperFragment2(Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, int start, String entityName, String fullEntityName) {

        String paramFragment = FragmentGenUtils.createParamFragment(methodName);
        boolean needList = false;
        if (methodName.startsWith("getAll")) {
            entityName = String.format("List<%s>", entityName);
            needList = true;
        }
        document.insertString(start, "    " + entityName + " ");
        document.insertString(start + entityName.length() + 5, methodName);
        document.insertString(start + entityName.length() + 5 + methodName.length(), paramFragment + ";\n");

        addEntityImport(document, containingClass, entityName, fullEntityName, needList);

        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, document);
        return true;
    }

    private void gen(@NotNull Project project, Editor editor, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName) {
        String entityName = null;
        PsiDocComment docComment = containingClass.getDocComment();
        if (docComment != null) {
            PsiDocTag tag = docComment.findTagByName("entity");
            if (tag != null) {
                PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement != null) {
                    entityName = valueElement.getText();
                }
            }
        }
        if (entityName == null) {
            MsgDialog msgDialog = new MsgDialog(project, "please add '/** @entity entityName */' in doc comment.");
            msgDialog.show();
            return;
        }

        String fullEntityName = null;
        if (entityName.contains(".")) {
            fullEntityName = entityName;
            entityName = StringUtils.substringAfterLast(entityName, ".");
        }

        @NotNull PsiMethod[] allMethods = containingClass.getAllMethods();
        for (PsiMethod allMethod : allMethods) {
            if (StringUtils.equals(allMethod.getName(), methodName)){
                MsgDialog msgDialog = new MsgDialog(project, "method exists.");
                msgDialog.show();
                return;
            }
        }

        boolean isContinue = genMapperXmlFragment(project, psiDocumentManager, containingClass, docComment, methodName);
        if (isContinue){
            isContinue = genMapperFragment1(project, editor, psiDocumentManager, document, containingClass, methodName, entityName, fullEntityName);
            if (isContinue){
                MsgDialog msgDialog = new MsgDialog(project, "Success!");
                msgDialog.show();
            }
        }

    }

    private boolean genMapperFragment1(Project project, Editor editor, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, String entityName, String fullEntityName) {

        String paramFragment = FragmentGenUtils.createParamFragment(methodName);
        int start = editor.getSelectionModel().getSelectionEnd() - methodName.length();

        boolean needList = false;
        if (methodName.startsWith("getAll")) {
            entityName = String.format("List<%s>", entityName);
            needList = true;
        }
        document.insertString(start, entityName + " ");
        document.insertString(start + methodName.length() + entityName.length() + 1, paramFragment + ";");
        int newEnd = start + methodName.length() + entityName.length() + 1 + paramFragment.length() + 1;
        editor.getSelectionModel().setSelection(newEnd, newEnd);

        addEntityImport(document, containingClass, entityName, fullEntityName, needList);

        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, document);
        return true;
    }

    private void addEntityImport(Document document, PsiClass containingClass, String entityName, String fullEntityName, boolean needList) {
        if (containingClass.getContainingFile() instanceof PsiJavaFile) {
            PsiJavaFile containingFile = (PsiJavaFile) containingClass.getContainingFile();
            PsiImportList importList = containingFile.getImportList();

            StringBuilder importStr = new StringBuilder();
            if (fullEntityName != null) {
                addImportIfNotExist(importList, fullEntityName,importStr);
            } else {
                // todo find entity class
            }

            if (needList){
                addImportIfNotExist(importList, "java.util.List",importStr);
            }

            addImportIfNotExist(importList, "org.apache.ibatis.annotations.Param",importStr);

            PsiPackageStatement packageStatement = containingFile.getPackageStatement();
            if (packageStatement != null && StringUtils.isNotBlank(importStr)) {
                document.insertString(packageStatement.getTextRange().getEndOffset(), "\n"+importStr.toString());
            }
        }
    }

    private void addImportIfNotExist(PsiImportList importList, String fullName, StringBuilder sb){
        if (importList != null) {
            PsiImportStatementBase entityImport = importList.findSingleClassImportStatement(fullName);
            if (entityImport == null) {
                entityImport = importList.findSingleImportStatement(fullName);
                if (entityImport == null) {
                    sb.append("import ").append(fullName).append(";\n");
                }
            }
        }
    }

    private boolean genMapperXmlFragment(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass mapperClass, PsiDocComment mapperDocComment, String methodName) {

        String qualifiedName = mapperClass.getQualifiedName();
        VirtualFile mapperXmlFile = findMapperXmlByName(qualifiedName, ProjectUtil.guessProjectDir(project));

        String tableName = null;
        String entityFullName = null;
        if (mapperDocComment != null) {
            PsiDocTag tableTag = mapperDocComment.findTagByName("table");
            if (tableTag != null) {
                PsiDocTagValue valueElement = tableTag.getValueElement();
                if (valueElement != null) {
                    tableName = valueElement.getText();
                }
            }

            PsiDocTag entityTag = mapperDocComment.findTagByName("entity");
            if (entityTag != null) {
                PsiDocTagValue valueElement = entityTag.getValueElement();
                if (valueElement != null) {
                    entityFullName = valueElement.getText();
                }
            }
        }
        if (tableName == null) {
            MsgDialog msgDialog = new MsgDialog(project, "please add '/** @table schema.table */' in doc comment.");
            msgDialog.show();
            return false;
        }

        try {
            Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
            if (xmldoc != null) {
                SAXBuilder sb = new SAXBuilder();
                StringReader xmlStringReader = new StringReader(xmldoc.getText());
                org.jdom.Document doc = sb.build(xmlStringReader);
                Element root = doc.getRootElement();

                // Base_Column_List and ResultMap
                boolean isContinue = createXmlResultMapAndColumnList(project, psiDocumentManager, tableName, entityFullName, xmldoc, root);
                if (!isContinue){
                    return false;
                }

                List<Attribute> idAttrs = (List<Attribute>) XPath.selectNodes(root, "//mapper/select/@id");

                if (idAttrs != null && !idAttrs.isEmpty()) {
                    Attribute found = null;
                    for (Attribute idAttr : idAttrs) {
                        String id = idAttr.getValue();
                        if (StringUtils.equals(id, methodName)) {
                            found = idAttr;
                            break;
                        }
                    }
                    if (found == null) {
                        int insertPos = xmldoc.getText().indexOf("</mapper>");
                        String sql = "\n" + FragmentGenUtils.createXmlFragment(methodName, tableName) + "\n";
                        xmldoc.insertString(insertPos, sql);
                        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
                    } else {
                        // todo replace
                    }
                } else {
                    int insertPos = xmldoc.getText().indexOf("</mapper>");
                    String sql = "\n" + FragmentGenUtils.createXmlFragment(methodName, tableName) + "\n";
                    xmldoc.insertString(insertPos, sql);
                    PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            MsgDialog msgDialog = new MsgDialog(project, e.getMessage());
            msgDialog.show();
            return false;
        }
        return true;
    }

    private boolean createXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, String tableFullName, String entityFullName, Document xmldoc, Element root) throws JDOMException {
        String jdbcUrl = getProperty("jdbcUrl", project);
        String username = getUserName(project);
        String password = getPassword(project);
        if (StringUtils.isBlank(jdbcUrl) || StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            JdbcForm jdbcForm = new JdbcForm(project);
            jdbcForm.show(form -> {
                try {
                    String jdbcUrl1 = StringUtils.trimToNull(form.getData().get("jdbcUrl"));
                    String username1 = StringUtils.trimToNull(form.getData().get("username"));
                    String password1 = StringUtils.trimToNull(form.getData().get("password"));
                    MysqlDataSource dataSource = new MysqlDataSource();
                    dataSource.setUrl(jdbcUrl1);
                    dataSource.setUser(username1);
                    dataSource.setPassword(password1);
                    JdbcTemplate jdbcTemplate = new JdbcTemplate();
                    jdbcTemplate.setDataSource(dataSource);

                    String sql = "select 1";
                    String result = jdbcTemplate.queryForObject(sql, String.class);

                    if (!"1".equals(result)) {
                        MsgDialog msgDialog = new MsgDialog(project, "fail");
                        msgDialog.show();
                        return;
                    }

                    form.setOk(true);
                    form.close(OK_EXIT_CODE);
                } catch (Exception e) {
                    e.printStackTrace();
                    MsgDialog msgDialog = new MsgDialog(project, e.getMessage());
                    msgDialog.show();
                }
            }, form -> {
                try {
                    String jdbcUrl12 = form.getData().get("jdbcUrl").trim();
                    String username12 = form.getData().get("username").trim();
                    String password12 = form.getData().get("password").trim();

                    MysqlDataSource dataSource = new MysqlDataSource();
                    dataSource.setUrl(jdbcUrl12);
                    dataSource.setUser(username12);
                    dataSource.setPassword(password12);
                    JdbcTemplate jdbcTemplate = new JdbcTemplate();
                    jdbcTemplate.setDataSource(dataSource);

                    String sql = "select 1";
                    String result = jdbcTemplate.queryForObject(sql, String.class);

                    if (!"1".equals(result)) {
                        MsgDialog msgDialog = new MsgDialog(project, "fail");
                        msgDialog.show();
                    }

                    form.setOk(false);

                } catch (Exception e) {
                    e.printStackTrace();
                    MsgDialog msgDialog = new MsgDialog(project, e.getMessage());
                    msgDialog.show();
                }
            });
            if (jdbcForm.isOk()){
                jdbcUrl = getProperty("jdbcUrl", project);
                username = getUserName(project);
                password = getPassword(project);
                doCreateXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc, root, jdbcUrl, username, password);
            }
            return jdbcForm.isOk();
        }else{
            doCreateXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc, root, jdbcUrl, username, password);
        }
        return true;
    }


    private void doCreateXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, String tableFullName, String entityFullName, Document xmldoc, Element root, String jdbcUrl,String username, String password) throws JDOMException {

        List<Element> columnListElements = (List<Element>) XPath.selectNodes(root, "//mapper/sql");
        Element columnListElement = null;
        for (Element element : columnListElements) {
            String id = element.getAttributeValue("id");
            if (StringUtils.equals(id, "Base_Column_List")) {
                columnListElement = element;
                break;
            }
        }
        List<Element> resultMapElements = (List<Element>) XPath.selectNodes(root, "//mapper/resultMap");
        Element resultMapElement = null;
        for (Element element : resultMapElements) {
            String id = element.getAttributeValue("id");
            if (StringUtils.equals(id, "BaseResultMap")) {
                resultMapElement = element;
                break;
            }
        }

        if (columnListElement != null && resultMapElement != null) return;

        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(dataSource);

        if (tableFullName == null || !tableFullName.contains(".")) {
            MsgDialog msgDialog = new MsgDialog(project, "please add '/** @table schema.table */' in doc comment.");
            msgDialog.show();
            return;
        }

        String[] split = tableFullName.split("\\.");
        String schema = split[0];
        String tableName = split[1];

        String sql = String.format("select * from information_schema.COLUMNS where TABLE_SCHEMA = '%s' and TABLE_NAME = '%s'", schema, tableName);
        List<Column> columns = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Column.class));
        if (CollectionUtils.isEmpty(columns)) {
            throw new RuntimeException("table not exist");
        }


        if (columnListElement == null) {
            String[] lines = xmldoc.getText().split("\n");
            String mapperStr = PatternUtil.getFirstMatch(Arrays.asList(lines), Pattern.compile("(<mapper.*>)"));
            if (mapperStr != null) {
                int insertPos = xmldoc.getText().indexOf(mapperStr) + mapperStr.length();
                String columnList = "\n" + FragmentGenUtils.createXmlColumnList(MapperUtil.createMapperXmlColumnListEntity(columns)) + "\n";
                xmldoc.insertString(insertPos, columnList);
            }
        }

        if (resultMapElement == null) {
            if (entityFullName == null || !entityFullName.contains(".")) {
                MsgDialog msgDialog = new MsgDialog(project, "please add '/** @entity entityFullName */' in doc comment.");
                msgDialog.show();
                return;
            }

            String[] lines = xmldoc.getText().split("\n");
            String mapperStr = PatternUtil.getFirstMatch(Arrays.asList(lines), Pattern.compile("(<mapper.*>)"));
            if (mapperStr != null) {
                int insertPos = xmldoc.getText().indexOf(mapperStr) + mapperStr.length();
                String resultMap = "\n" + FragmentGenUtils.createXmlResultMap(MapperUtil.createMapperXmlResultMapEntity(entityFullName, columns)) + "\n";
                xmldoc.insertString(insertPos, resultMap);
            }
        }

        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
    }


    private static Map<String, VirtualFile> mapper2xmlMap = new HashMap<>();

    private static VirtualFile findMapperXmlByName(String mapperFullName, VirtualFile root) {
        VirtualFile xmlPath = mapper2xmlMap.get(mapperFullName);
        if (xmlPath != null) {
            if (xmlPath.exists()) {
                return xmlPath;
            } else {
                mapper2xmlMap.remove(mapperFullName);
            }
        }

        VirtualFile projectDir = root;
        if (root != null){
            root = root.findFileByRelativePath("src/main");
        }
        if (root == null) root = projectDir;

        final VirtualFile[] found = {null};
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.isDirectory()) return super.visitFile(file);
                if (found[0] != null) return false;
                String extension = file.getExtension();
                if (StringUtils.equals(extension, "xml")) {
                    Document xmldoc = fileDocumentManager.getDocument(file);
                    if (xmldoc != null) {
                        final String text = xmldoc.getText();
                        if (!text.contains(mapperFullName)) return false;
                        try (StringReader stringReader = new StringReader(text)){
                            SAXBuilder sb = new SAXBuilder();
                            org.jdom.Document doc = sb.build(stringReader);
                            Attribute namespaceText = (Attribute) XPath.selectSingleNode(doc.getRootElement(), "//mapper/@namespace");
                            if (namespaceText != null) {
                                String namespace = namespaceText.getValue();
                                if (StringUtils.equals(namespace, mapperFullName)) {
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

    private String getPrevWord(Document document, Editor editor) {
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();
        if (selectionEnd == 0) return null;
        int len = 1;
        while (len < 50) {
            String text = document.getText(TextRange.create(selectionEnd - len, selectionEnd));
            if (text.startsWith(" ") || text.startsWith("\n") || text.startsWith("\r\n") || text.startsWith("\t")) {
                return text.substring(1);
            }
            len++;
        }
        return document.getText(TextRange.create(selectionEnd - len, selectionEnd));
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        String methodName = getPrevWord(editor.getDocument(), editor);
        if (containingClass != null
                && StringUtils.isNotBlank(methodName)
                && methodName.startsWith("get")
                && containingClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null) {
            return true;
        }

        try {
            String methodName2 = element.getPrevSibling().getFirstChild().getLastChild().getText();
            if (StringUtils.isNotBlank(methodName2) && methodName2.startsWith("get")) {
                PsiClass psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild().getFirstChild()).resolve()).getType()).resolve();
                if (psiClass != null && psiClass.isInterface() && psiClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null) {
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null) {
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {

        }

        return false;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "generate mybatis fragment";
    }

    @NotNull
    @Override
    public String getText() {
        return "generate mybatis fragment";
    }
}
