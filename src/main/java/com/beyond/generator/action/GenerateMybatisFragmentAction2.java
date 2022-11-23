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
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
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
public class GenerateMybatisFragmentAction2 extends PsiElementBaseIntentionAction {
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);


        PsiFile containingFile = element.getContainingFile();
        Document document = psiDocumentManager.getDocument(containingFile);
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);

        if (containingClass.isInterface()) {
            gen(project, psiDocumentManager, containingClass);
        } else {
            try {
                PsiClass psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild()).resolve()).getType()).resolve();
                if (psiClass != null && psiClass.isInterface() && psiClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null) {
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null) {
                        gen2(project, psiDocumentManager, psiClass);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void gen2(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass containingClass) {
        PsiDocComment docComment = containingClass.getDocComment();
        final boolean isContinue = genMapperXmlResultMapAndColumnList(project, psiDocumentManager, containingClass, docComment);
        if (isContinue){
            MsgDialog msgDialog = new MsgDialog(project, "Success!");
            msgDialog.show();
        }
    }

    private void gen(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass containingClass) {
        PsiDocComment docComment = containingClass.getDocComment();
        final boolean isContinue = genMapperXmlResultMapAndColumnList(project, psiDocumentManager, containingClass, docComment);
        if (isContinue){
            MsgDialog msgDialog = new MsgDialog(project, "Success!");
            msgDialog.show();
        }
    }


    private boolean genMapperXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass mapperClass, PsiDocComment mapperDocComment) {

        String qualifiedName = mapperClass.getQualifiedName();
        VirtualFile mapperXmlFile = findMapperXmlByName(qualifiedName, ProjectUtil.guessProjectDir(project));

        String tableFullName = null;
        String entityFullName = null;
        if (mapperDocComment != null) {
            PsiDocTag tableTag = mapperDocComment.findTagByName("table");
            if (tableTag != null) {
                PsiDocTagValue valueElement = tableTag.getValueElement();
                if (valueElement != null) {
                    tableFullName = valueElement.getText();
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


        try {
            Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
            if (xmldoc != null) {
                SAXBuilder sb = new SAXBuilder();
                StringReader xmlStringReader = new StringReader(xmldoc.getText());
                org.jdom.Document doc = sb.build(xmlStringReader);
                Element root = doc.getRootElement();

                List<Element> columnListElements = (List<Element>) XPath.selectNodes(root, "//mapper/sql");
                Element columnListElement = null;
                for (Element element : columnListElements) {
                    String id = element.getAttributeValue("id");
                    if (StringUtils.equals(id, "Base_Column_List")) {
                        columnListElement  = element;
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

                if (columnListElement != null && resultMapElement != null) return true;


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
                        doCreateXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc, columnListElement, resultMapElement, jdbcUrl, username, password);
                    }
                    if (jdbcForm.isOk()) return true;
                }else {
                    doCreateXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc, columnListElement, resultMapElement, jdbcUrl, username, password);
                    return true;
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

    private void doCreateXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, String tableFullName, String entityFullName, Document xmldoc, Element columnListElement, Element resultMapElement, String jdbcUrl, String username, String password) {

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
            if (mapperStr!=null){
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
            if (mapperStr!=null) {
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
        if (containingClass != null
                && containingClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null
                && PsiTreeUtil.findFirstParent(element, psiElement -> psiElement instanceof PsiDocComment) != null) {
            return true;
        }

        try {
            PsiClass psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild()).resolve()).getType()).resolve();
            if (psiClass != null && psiClass.isInterface() && psiClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null) {
                VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                if (classDocument != null) {
                    return true;
                }
            }
        } catch (Exception ignore) {

        }


        return false;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "generate mybatis result map";
    }

    @NotNull
    @Override
    public String getText() {
        return "generate mybatis result map";
    }
}
