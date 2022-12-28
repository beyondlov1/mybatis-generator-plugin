package com.beyond.generator.action;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.generator.PathUtils;
import com.beyond.generator.PluginUtils;
import com.beyond.generator.ui.CopyableMsgDialog;
import com.beyond.generator.ui.EntityForm;
import com.beyond.generator.ui.EntityNameForm;
import com.beyond.generator.utils.MybatisToSqlUtils;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.beyond.generator.utils.PsiFileUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.beyond.generator.utils.MapperUtil.msg;
import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * @author chenshipeng
 * @date 2022/11/08
 */
public class MyBatisSql2EntityAction extends PsiElementBaseIntentionAction {
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

        try {

            SAXBuilder sb = new SAXBuilder();
            String selectedText = editor.getSelectionModel().getSelectedText();
            if (StringUtils.isBlank(selectedText)) return;

            StringReader xmlStringReader = new StringReader(selectedText);
            org.jdom.Document doc = sb.build(xmlStringReader);
            Element selectElement = doc.getRootElement();

            String id = selectElement.getAttributeValue("id");
            String sql = MybatisToSqlUtils.toSql(editor.getDocument().getText(), id);

            SQLStatement sqlStatement = SQLUtils.parseSingleMysqlStatement(sql);

            List<String> propertyNames = new ArrayList<>();
            List<SQLSelectItem> selectList = ((SQLSelectStatement) sqlStatement).getSelect().getQueryBlock().getSelectList();
            for (SQLSelectItem selectItem : selectList) {
                String propertyName = selectItem.getExpr().toString();
                String alias = selectItem.getAlias2();
                if (alias == null){
                    if (selectItem.getExpr() instanceof SQLPropertyExpr){
                        propertyName = ((SQLPropertyExpr) selectItem.getExpr()).getName();
                    }
                    if (propertyName.contains("_")){
                        propertyName = com.beyond.generator.StringUtils.lineToHump(propertyName);
                        selectItem.setAlias(propertyName);
                    }
                }else {
                    propertyName = alias;
                }
                propertyNames.add(propertyName);
            }

            EntityNameForm entityNameForm = new EntityNameForm(project);
            entityNameForm.show(form -> {
                form.setOk(true);
                form.close(OK_EXIT_CODE);
            });
            if (entityNameForm.isOk()){
                generateEntity(project, false,entityNameForm.getData().get("packageName"), entityNameForm.getData().get("entityName"), propertyNames);

                String target = extractSelect(sqlStatement.toString());
                String forReplace = extractSelect(extractContent(selectedText));
                if (StringUtils.isNotBlank(forReplace) && StringUtils.isNotBlank(target) && !forReplace.contains("<")){
                    String replaced = selectedText.replace(forReplace, " "+target+" ");
                    editor.getDocument().replaceString(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd(), replaced);
                }
                msg(project, "Success!");
                CopyableMsgDialog.show(project, entityNameForm.getData().get("packageName")+"."+entityNameForm.getData().get("entityName"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            msg(project, e.getMessage());
        }
    }

    Pattern pattern = Pattern.compile("select(.*?)from", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE|Pattern.DOTALL);


    private String extractSelect(String sql){
        if (sql == null) return null;
        Matcher matcher = pattern.matcher(sql);
        if(matcher.find()){
            return matcher.group(1);
        }
        return null;
    }

    Pattern pattern2 = Pattern.compile("<.*?>(.*)</.*?>", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE|Pattern.DOTALL);
    private String extractContent(String sql){
        if (sql == null) return null;
        Matcher matcher = pattern2.matcher(sql);
        if(matcher.find()){
            return matcher.group(1);
        }
        return null;
    }


    @Nullable
    private static Element getElementByNameAndAttr(Element root, String xpath, String attr, String attrTarget) throws JDOMException {
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

    private boolean generateEntity(@NotNull Project project, boolean useCache,String packageName, String entityName, List<String> fieldNames) {
        learn(project, useCache);

        JavaEntity javaEntity = new JavaEntity();

        List<String> imports = new ArrayList<>();
        imports.add("lombok.Data");
        javaEntity.setImports(imports);
        javaEntity.setPackageName(packageName);
        javaEntity.setClassName(entityName);

        List<JavaEntity.FieldEntity> fieldEntities = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String type = predict(fieldName);
            if (type == null){
                type = "String";
            }
            JavaEntity.FieldEntity fieldEntity = new JavaEntity.FieldEntity();
            fieldEntity.setName(fieldName);
            fieldEntity.setType(type);
            fieldEntities.add(fieldEntity);
        }
        javaEntity.setFields(fieldEntities);

        for (JavaEntity.FieldEntity fieldEntity : fieldEntities) {
            @NotNull PsiClass[] psiClasses = PsiShortNamesCache.getInstance(project).getClassesByName(fieldEntity.getType(), GlobalSearchScope.allScope(project));
            PsiClass selectPsiClass = null;
            for (PsiClass psiClass : psiClasses) {
                if (psiClass.getQualifiedName() != null && psiClass.getQualifiedName().startsWith("java")){
                    selectPsiClass = psiClass;
                    break;
                }
            }
            if (selectPsiClass == null && psiClasses.length > 0){
                selectPsiClass = psiClasses[0];
            }

            if (selectPsiClass != null){
                if (!javaEntity.getImports().contains(selectPsiClass.getQualifiedName())){
                    javaEntity.getImports().add(selectPsiClass.getQualifiedName());
                }
            }
        }


        String javaPath = PluginUtils.getProjectJavaPath(project);
        String targetDir = PathUtils.concat(javaPath, javaEntity.getPackageName().split("\\."));
        PsiFileUtil.writeFromTemplate(project, PathUtils.concat(targetDir, javaEntity.getClassName() + ".java"), javaEntity.toGen(false), "entity.ftl");
        return false;
    }


    private void addEntityImport(Document document, PsiClass containingClass, String fullImportClassName, boolean needList) {
        addEntityImport(document, containingClass.getContainingFile(), fullImportClassName, needList);
    }

    private void addEntityImport(Document document, PsiFile psiFile, String fullImportClassName, boolean needList) {
        if (psiFile instanceof PsiJavaFile) {
            PsiJavaFile containingFile = (PsiJavaFile) psiFile;
            PsiImportList importList = containingFile.getImportList();

            StringBuilder importStr = new StringBuilder();
            if (fullImportClassName != null) {
                addImportIfNotExist(importList, fullImportClassName, importStr);
            }

            if (needList) {
                addImportIfNotExist(importList, "java.util.List", importStr);
            }

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

    private String[] trimSplit(String str, String sep){
        String[] split = StringUtils.split(str, sep);
        if (split == null) return null;
        for (int i = 0; i < split.length; i++) {
            split[i] = StringUtils.trim(split[i]);
        }
        return split;
    }

    private Map<String, Map<String, Integer>> fieldName2TypeMap = new HashMap<>();

    private void learn(Project project, boolean useCache){
        if (useCache && !fieldName2TypeMap.isEmpty()){
            return;
        }
        fieldName2TypeMap = new HashMap<>();
        List<PsiClass> allClasses = new ArrayList<>();
        PsiShortNamesCache psiShortNamesCache = PsiShortNamesCache.getInstance(project);
        @NotNull String[] allClassNames = psiShortNamesCache.getAllClassNames();
        for (String allClassName : allClassNames) {
            @NotNull PsiClass[] psiClasses = psiShortNamesCache.getClassesByName(allClassName, GlobalSearchScope.projectScope(project));
            allClasses.addAll(Arrays.asList(psiClasses));
        }

        List<PsiField> allFields = new ArrayList<>();
        for (PsiClass allClass : allClasses) {
            if (allClass.getAnnotation("lombok.Data") == null){
                continue;
            }
            @NotNull PsiField[] fields = allClass.getFields();
            allFields.addAll(Arrays.asList(fields));
        }

        for (PsiField allField : allFields) {
            fieldName2TypeMap.computeIfAbsent(allField.getName(), k -> new HashMap<>());
            Map<String, Integer> type2CountMap = fieldName2TypeMap.get(allField.getName());
            type2CountMap.putIfAbsent(allField.getType().getPresentableText(), 0);
            type2CountMap.put(allField.getType().getPresentableText(), type2CountMap.get(allField.getType().getPresentableText()) + 1);
        }
    }

    private String predict(String fieldName){

        Map<String, Integer> type2CountMap = fieldName2TypeMap.get(fieldName);
        if (type2CountMap == null) return null;
        String result = null;
        Integer max = 0;
        for (Map.Entry<String, Integer> entry : type2CountMap.entrySet()) {
            if (entry.getValue() > max){
                result = entry.getKey();
                max = entry.getValue();
            }
        }
        return result;
    }




    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        VirtualFile file = fileDocumentManager.getFile(editor.getDocument());
        if (file == null) return false;
        String name = file.getName();
        if (name.endsWith(".xml")) {
            String selectedText = editor.getSelectionModel().getSelectedText();
            if (StringUtils.isNotBlank(selectedText)) {
                return selectedText.trim().startsWith("<select");
            }
        }
        return false;
    }



    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "generate entity";
    }

    @NotNull
    @Override
    public String getText() {
        return "generate entity";
    }
}
