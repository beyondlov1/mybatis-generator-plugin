package com.beyond.generator.action;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.beyond.gen.freemarker.FragmentGenUtils;
import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.generator.PathUtils;
import com.beyond.generator.PluginUtils;
import com.beyond.generator.dom.IdDomElement;
import com.beyond.generator.dom.Mapper;
import com.beyond.generator.ui.EntityNameForm;
import com.beyond.generator.ui.JdbcForm;
import com.beyond.generator.ui.SQLForm;
import com.beyond.generator.utils.MapperUtil;
import com.beyond.generator.utils.MybatisToSqlUtils;
import com.beyond.generator.utils.PerformanceUtil;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.beyond.generator.utils.PsiFileUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.beyond.generator.utils.MapperUtil.*;
import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * generate mybatis fragment
 * @author chenshipeng
 * @date 2022/11/08
 */
public class GenerateMybatisFragmentFromSQLAction extends GenerateMyBatisBaseAction {

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        PsiFile containingFile = element.getContainingFile();
        Document document = psiDocumentManager.getDocument(containingFile);
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);

        PsiClass psiClass;
        try {
            String methodName = element.getPrevSibling().getFirstChild().getLastChild().getText();
            psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild().getFirstChild()).resolve()).getType()).resolve();
            if (isMapperClass(psiClass)) {
                VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                if (classDocument != null) {

                    SQLForm sqlForm = new SQLForm(project);
                    sqlForm.show(form -> {
                        form.setOk(true);
                        form.close(OK_EXIT_CODE);
                    });
                    if (sqlForm.isOk()){
                        EntityNameForm entityNameForm = new EntityNameForm(project);
                        entityNameForm.show(form -> {
                            form.setOk(true);
                            form.close(OK_EXIT_CODE);
                        });
                        if (entityNameForm.isOk()){
                            WriteCommandAction.runWriteCommandAction(project, (ThrowableComputable<Object, Exception>) () -> {
                                String sql = sqlForm.getData().get("sql");
                                String packageName = entityNameForm.getData().get("packageName");
                                String entityName = entityNameForm.getData().get("entityName");
                                gen2(project, psiDocumentManager, classDocument, psiClass, methodName, sql, packageName, entityName);
                                return null;
                            });
                            msgDialog(project, "Success!");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            msgDialog(project, e.getMessage());
        }
    }

    private void gen2(@NotNull Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, String mybatisSql, String packageName, String entityName) throws JDOMException, IOException {
        @NotNull PsiMethod[] allMethods = containingClass.getAllMethods();
        for (PsiMethod allMethod : allMethods) {
            if (StringUtils.equals(allMethod.getName(), methodName)) {
                throw new RuntimeException("method exists.");
            }
        }

        String resultType = packageName + "." + entityName;


        List<String> propertyNames = new ArrayList<>();

        String replacedSql = "";
        if (StringUtils.isNotBlank(mybatisSql)){

            if (!mybatisSql.trim().toLowerCase().startsWith("select")){
                mybatisSql = "select "+mybatisSql.trim();
            }

            String mybatisSqlXml = "<select>" + mybatisSql + "</select>";
            String sql = MybatisToSqlUtils.toSql(mybatisSqlXml);
            SQLStatement sqlStatement = SQLUtils.parseSingleMysqlStatement(sql);
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
            // 处理sql, 替换select
            replacedSql = mybatisSql.replace(extractSelect(mybatisSql),extractSelect(sqlStatement.toString()));

        }

        generateEntity(project, false, packageName, entityName, propertyNames);


        genMapperXmlFragment(project, psiDocumentManager, containingClass, methodName, replacedSql, resultType);
        genMapperFragment2(project, psiDocumentManager, document, containingClass, methodName, entityName, resultType, mybatisSql);
    }

    Pattern pattern = Pattern.compile("select(.*?)from", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE|Pattern.DOTALL);
    private String extractSelect(String sql){
        if (sql == null) return "";
        Matcher matcher = pattern.matcher(sql);
        if(matcher.find()){
            return matcher.group(1);
        }
        return "";
    }

    private void generateEntity(@NotNull Project project, boolean useCache,String packageName, String entityName, List<String> fieldNames) {
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

    private static Pattern mybatisForeachPattern = Pattern.compile("<foreach.*?collection.*?=.*?\"(.*?)\".*?>.*?</foreach>", Pattern.MULTILINE|Pattern.DOTALL);
    private static Pattern mybatisVarPattern = Pattern.compile("\\#\\{(.*?)\\}");
    private static Pattern mybatisVar2Pattern = Pattern.compile("\\#(\\w+)");

    private boolean genMapperFragment2(Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, String entityName, String fullEntityName, String mybatisSql) {

        String mybatisSqlForExtract = mybatisSql;

        List<String> fields = new ArrayList<>();

        // 处理foreach
        Matcher foreachMatcher = mybatisForeachPattern.matcher(mybatisSqlForExtract);
        while (foreachMatcher.find()){
            fields.add(foreachMatcher.group(1));
        }
        mybatisSqlForExtract = foreachMatcher.replaceAll("");

        // 提取 #{}
        Matcher matcher = mybatisVarPattern.matcher(mybatisSqlForExtract);
        while (matcher.find()){
            fields.add(matcher.group(1));
        }

        // 提取 #xxx
        Matcher matcher2 = mybatisVar2Pattern.matcher(mybatisSqlForExtract);
        while (matcher2.find()){
            fields.add(matcher2.group(1));
        }


        int start = document.getText().lastIndexOf("}");
        String paramFragment = String.format("(%s)", FragmentGenUtils.generateParams(fields));
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

    private boolean genMapperXmlFragment(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass mapperClass, String methodName, String sql, String resultType) throws JDOMException, IOException {

        String qualifiedName = mapperClass.getQualifiedName();
        Mapper mapper = findMapperXmlByName(project, qualifiedName);
        VirtualFile mapperXmlFile = toVirtualFile(mapper);

        if (mapperXmlFile == null) return false;
        Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
        if (xmldoc != null) {
            Optional<IdDomElement> selectOptional = mapper.getSelects().stream().filter(x -> StringUtils.equals(x.getId().getValue(), methodName)).findFirst();
            if (!selectOptional.isPresent()){
                int insertPos = xmldoc.getText().indexOf("</mapper>");
                String sqlStr = "\n" + FragmentGenUtils.createXmlFragmentFromSql(sql, methodName, resultType) + "\n";
                xmldoc.insertString(insertPos, sqlStr);
                PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
            } else {
                // todo replace
            }
        }
        return true;
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
        return "generate mybatis fragment from sql";
    }

    @NotNull
    @Override
    public String getText() {
        return "generate mybatis fragment from sql";
    }
}
