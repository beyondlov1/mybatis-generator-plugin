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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
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
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.beyond.generator.utils.MapperUtil.*;
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
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);

        if (isMapperClass(containingClass)) {
            try {
                gen(project, psiDocumentManager, containingClass);
            } catch (JDOMException | IOException e) {
                e.printStackTrace();
                msg(project, e.getMessage());
            }
        } else {
            try {
                PsiClass psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild()).resolve()).getType()).resolve();
                if (isMapperClass(psiClass)) {
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null) {
                        gen2(project, psiDocumentManager, psiClass);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                msg(project, e.getMessage());
            }
        }
    }

    private void gen2(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass containingClass) throws JDOMException, IOException {
        PsiDocComment docComment = containingClass.getDocComment();
        final boolean isContinue = genMapperXmlResultMapAndColumnList(project, psiDocumentManager, containingClass, docComment);
        if (isContinue){
            msg(project, "Success!");
        }
    }

    private void gen(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass containingClass) throws JDOMException, IOException {
        PsiDocComment docComment = containingClass.getDocComment();
        final boolean isContinue = genMapperXmlResultMapAndColumnList(project, psiDocumentManager, containingClass, docComment);
        if (isContinue){
            msg(project, "Success!");
        }
    }


    private boolean genMapperXmlResultMapAndColumnList(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass mapperClass, PsiDocComment mapperDocComment) throws JDOMException, IOException {
        
        VirtualFile mapperXmlFile = findMapperXmlByName(mapperClass.getQualifiedName(), ProjectUtil.guessProjectDir(project));

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


        Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
        if (xmldoc != null) {
            SAXBuilder sb = new SAXBuilder();
            StringReader xmlStringReader = new StringReader(xmldoc.getText());
            org.jdom.Document doc = sb.build(xmlStringReader);
            Element root = doc.getRootElement();

            Element columnListElement = getElementByNameAndAttr(root, "//mapper/sql", "id", "Base_Column_List");
            Element resultMapElement = getElementByNameAndAttr(root, "//mapper/resultMap", "id",  "BaseResultMap");

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
                            throw new RuntimeException( "fail");
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
                            throw new RuntimeException( "fail");
                        }
                        form.setOk(false);

                    } catch (Exception e) {
                        e.printStackTrace();
                        msg(project, e.getMessage());
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
            throw new RuntimeException( "please add '/** @table schema.table */' in doc comment.");
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
                throw new RuntimeException( "please add '/** @entity entityFullName */' in doc comment.");
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
            if (isMapperClass(psiClass)) {
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
