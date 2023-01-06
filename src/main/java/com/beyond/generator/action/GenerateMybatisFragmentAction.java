package com.beyond.generator.action;

import com.beyond.gen.freemarker.FragmentGenUtils;
import com.beyond.generator.Column;
import com.beyond.generator.ui.JdbcForm;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatternUtil;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.beyond.generator.utils.MapperUtil.*;
import static com.beyond.generator.utils.PropertyUtil.*;
import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * generate mybatis fragment
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

        if (isMapperClass(containingClass)) {
            try {
                String methodName = getPrevWord(document, editor);
                if (StringUtils.isBlank(methodName)) return;
                gen(project, editor, psiDocumentManager, document, containingClass, methodName);
            } catch (JDOMException | IOException e) {
                e.printStackTrace();
                msg(project, e.getMessage());
            }
        } else {
            PsiClass psiClass;
            try {
                String methodName = element.getPrevSibling().getFirstChild().getLastChild().getText();
                psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild().getFirstChild()).resolve()).getType()).resolve();
                if (isMapperClass(psiClass)) {
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null) {
                        gen2(project, psiDocumentManager, classDocument, psiClass, methodName);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                msg(project, e.getMessage());
            }
        }
    }

    private void gen2(@NotNull Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName) throws JDOMException, IOException {
        String entityName = null;
        PsiDocComment docComment = containingClass.getDocComment();
        if (docComment != null) {
            PsiDocTag tag = docComment.findTagByName("entity");
            if (tag != null) {
                PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement != null) {
                    entityName = valueElement.getText();
                    entityName = completeFullEntityName(project, psiDocumentManager, entityName, docComment);
                }
            }
        }
        if (entityName == null) {
            throw new RuntimeException("please add '/** @entity entityName */' in doc comment.");
        }

        String fullEntityName = null;
        if (entityName.contains(".")) {
            fullEntityName = entityName;
            entityName = StringUtils.substringAfterLast(entityName, ".");
        }


        @NotNull PsiMethod[] allMethods = containingClass.getAllMethods();
        for (PsiMethod allMethod : allMethods) {
            if (StringUtils.equals(allMethod.getName(), methodName)) {
                throw new RuntimeException("method exists.");
            }
        }

        boolean isContinue = genMapperXmlFragment(project, psiDocumentManager, containingClass, docComment, methodName);
        if (isContinue) {
            isContinue = genMapperFragment2(project, psiDocumentManager, document, containingClass, methodName, entityName, fullEntityName);
            if (isContinue) {
                msg(project, "Success!");
            }
        }
    }

    private boolean genMapperFragment2(Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, String entityName, String fullEntityName) {

        int start = document.getText().lastIndexOf("}");
        String paramFragment = FragmentGenUtils.createParamFragment(methodName);
        boolean needList = false;
        if (methodName.startsWith("getAll")) {
            entityName = String.format("List<%s>", entityName);
            needList = true;
        }
        document.insertString(start, "    " + entityName + " ");
        document.insertString(start + entityName.length() + 5, methodName);
        document.insertString(start + entityName.length() + 5 + methodName.length(), paramFragment + ";\n");

        addEntityImport(project, document, containingClass, entityName, fullEntityName, needList);

        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, document);
        return true;
    }

    private void gen(@NotNull Project project, Editor editor, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName) throws JDOMException, IOException {
        String entityName = null;
        PsiDocComment docComment = containingClass.getDocComment();
        if (docComment != null) {
            PsiDocTag tag = docComment.findTagByName("entity");
            if (tag != null) {
                PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement != null) {
                    entityName = valueElement.getText();
                    entityName = completeFullEntityName(project, psiDocumentManager, entityName, docComment);
                }
            }
        }
        if (entityName == null) {
            throw new RuntimeException("please add '/** @entity entityName */' in doc comment.");
        }

        String fullEntityName = null;
        if (entityName.contains(".")) {
            fullEntityName = entityName;
            entityName = StringUtils.substringAfterLast(entityName, ".");
        }

        @NotNull PsiMethod[] allMethods = containingClass.getAllMethods();
        for (PsiMethod allMethod : allMethods) {
            if (StringUtils.equals(allMethod.getName(), methodName)) {
                throw new RuntimeException("method exists.");
            }
        }

        boolean isContinue = genMapperXmlFragment(project, psiDocumentManager, containingClass, docComment, methodName);
        if (isContinue) {
            isContinue = genMapperFragment1(project, editor, psiDocumentManager, document, containingClass, methodName, entityName, fullEntityName);
            if (isContinue) {
                msg(project, "Success!");
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

        addEntityImport(project, document, containingClass, entityName, fullEntityName, needList);

        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, document);
        return true;
    }

    private void addEntityImport(Project project, Document document, PsiClass containingClass, String entityName, String fullEntityName, boolean needList) {
        if (containingClass.getContainingFile() instanceof PsiJavaFile) {
            PsiJavaFile containingFile = (PsiJavaFile) containingClass.getContainingFile();
            PsiImportList importList = containingFile.getImportList();

            StringBuilder importStr = new StringBuilder();
            if (fullEntityName != null) {
                addImportIfNotExist(importList, fullEntityName, importStr);
            }

            if (needList) {
                addImportIfNotExist(importList, "java.util.List", importStr);
            }

            addImportIfNotExist(importList, "org.apache.ibatis.annotations.Param", importStr);

            PsiPackageStatement packageStatement = containingFile.getPackageStatement();
            if (packageStatement != null && StringUtils.isNotBlank(importStr)) {
                document.insertString(packageStatement.getTextRange().getEndOffset(), "\n" + importStr.toString());
            }
        }
    }

    private void addImportIfNotExist(PsiImportList importList, String fullName, StringBuilder sb) {
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

    private boolean genMapperXmlFragment(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass mapperClass, PsiDocComment mapperDocComment, String methodName) throws JDOMException, IOException {

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
                    completeFullEntityName(project, psiDocumentManager, entityFullName, mapperDocComment);
                }
            }
        }
        if (tableName == null) {
            throw new RuntimeException("please add '/** @table schema.table */' in doc comment.");
        }

        Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
        if (xmldoc != null) {
            SAXBuilder sb = new SAXBuilder();
            StringReader xmlStringReader = new StringReader(xmldoc.getText());
            org.jdom.Document doc = sb.build(xmlStringReader);
            Element root = doc.getRootElement();

            // Base_Column_List and ResultMap
            boolean isContinue = createXmlResultMapAndColumnList(project, psiDocumentManager, tableName, entityFullName, xmldoc, root);
            if (!isContinue) {
                return false;
            }

            Element select = getElementByNameAndAttr(root, "//mapper/select", "id", methodName);

            if (select == null) {
                int insertPos = xmldoc.getText().indexOf("</mapper>");
                String sql = "\n" + FragmentGenUtils.createXmlFragment(methodName, tableName) + "\n";
                xmldoc.insertString(insertPos, sql);
                PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
            } else {
                // todo replace
            }
        }
        return true;
    }

    private boolean createXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, String tableFullName, String entityFullName, Document xmldoc, Element root) throws JDOMException {

        Element columnListElement = getElementByNameAndAttr(root, "//mapper/sql", "id", "Base_Column_List");
        Element resultMapElement = getElementByNameAndAttr(root, "//mapper/resultMap", "id", "BaseResultMap");

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
                    boolean pass = testConnection(jdbcUrl1, username1, password1);
                    if (!pass) {
                        throw new RuntimeException("fail");
                    }

                    form.setOk(true);
                    form.close(OK_EXIT_CODE);
                } catch (Exception e) {
                    e.printStackTrace();
                    msg(project, e.getMessage());
                }
            }, form -> {
                try {
                    String jdbcUrl12 = form.getData().get("jdbcUrl").trim();
                    String username12 = form.getData().get("username").trim();
                    String password12 = form.getData().get("password").trim();

                    boolean pass = testConnection(jdbcUrl12, username12, password12);
                    if (!pass) {
                        throw new RuntimeException("fail");
                    }

                    form.setOk(false);

                } catch (Exception e) {
                    e.printStackTrace();
                    msg(project, e.getMessage());
                }
            });
            if (jdbcForm.isOk()) {
                jdbcUrl = getProperty("jdbcUrl", project);
                username = getUserName(project);
                password = getPassword(project);
                doCreateXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc,  resultMapElement, columnListElement, jdbcUrl, username, password);
            }
            return jdbcForm.isOk();
        } else {
            doCreateXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc, resultMapElement, columnListElement, jdbcUrl, username, password);
        }
        return true;
    }


    private void doCreateXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, String tableFullName, String entityFullName, Document xmldoc, Element resultMapElement, Element columnListElement, String jdbcUrl, String username, String password) throws JDOMException {

        if (columnListElement != null && resultMapElement != null) return;

        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(dataSource);

        if (tableFullName == null || !tableFullName.contains(".")) {
            throw new RuntimeException("please add '/** @table schema.table */' in doc comment.");
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
                throw new RuntimeException("please add '/** @entity entityFullName */' in doc comment.");
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


    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        if(containingClass == null) return false;
        String methodName = getPrevWord(editor.getDocument(), editor);
        if (StringUtils.isNotBlank(methodName) && methodName.startsWith("get") && containingClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null) {
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
