package com.beyond.generator.action;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.beyond.gen.freemarker.StringUtil;
import com.beyond.generator.dom.MapperLite;
import com.beyond.generator.utils.MybatisToSqlUtils;
import com.beyond.generator.utils.PerformanceUtil;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.beyond.generator.utils.MapperUtil.*;

/**
 * generate mybatis fragment
 *
 * @author chenshipeng
 * @date 2022/11/08
 */
public class GenerateMybatisInsertFragmentAction extends GenerateMyBatisBaseAction {
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        PsiFile containingFile = element.getContainingFile();
        Document document = psiDocumentManager.getDocument(containingFile);
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);

        try {
            PerformanceUtil.mark("invoke1");
            PsiClass psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getMethodExpression().getQualifier()).resolve()).getType()).resolve();

            PsiType argType = ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getArgumentList().getExpressionTypes()[0];
            PsiClass argumentPsiClass = null;
            boolean isList = false;
            if (argType instanceof PsiClassReferenceType ){
                argumentPsiClass = ((PsiClassReferenceType)argType ).resolve();
            }
            if (argType instanceof PsiClassType){
                PsiClass type = ((PsiClassType)argType).resolve();
                if (StringUtils.equals(type.getName(), "List") || StringUtils.equals(type.getName(), "Set")||StringUtils.equals(type.getName(), "Collection")){
                    argumentPsiClass = ((PsiClassType) ((PsiClassType) ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getArgumentList().getExpressions()[0].getType()).getParameters()[0]).resolve();
                    isList = true;
                }
            }

            if (argumentPsiClass == null) return;

            String argName = ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getArgumentList().getExpressions()[0].getText();

            String methodName = ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getMethodExpression().getLastChild().getText();
            if (isMapperClass(psiClass)) {
                VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                if (classDocument != null) {
                    PerformanceUtil.mark("invoke2");
                    gen2(project, psiDocumentManager, classDocument, psiClass, methodName, argumentPsiClass, argName, isList);
                    PerformanceUtil.mark("invoke3");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            msgE(project, e.getMessage());
        }

    }

    private void gen2(@NotNull Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, PsiClass argumentClass, String argName, boolean isList) throws JDOMException, IOException {
        boolean isContinue = genMapperXmlFragment(project, psiDocumentManager, containingClass, methodName, argumentClass, argName, isList);
        if (isContinue) {
            isContinue = genMapperFragment2(project, psiDocumentManager, document, containingClass, methodName, argumentClass, argName, isList);
            if (isContinue) {
                msg(project, "Success!");
            }
        }
    }


    private boolean genMapperXmlFragment(@NotNull Project project, PsiDocumentManager psiDocumentManager, PsiClass mapperClass, String methodName, PsiClass argumentClass, String argName, boolean isList) throws JDOMException, IOException {

        String qualifiedName = mapperClass.getQualifiedName();
        PerformanceUtil.mark("genMapperXmlFragment_1");
        MapperLite mapper = findMapperXmlByName(project, qualifiedName);
        VirtualFile mapperXmlFile = toVirtualFile(mapper);
        PerformanceUtil.mark("genMapperXmlFragment_2");

        String tableName = null;
        PsiDocComment mapperDocComment = mapperClass.getDocComment();
        if (mapperDocComment != null) {
            PsiDocTag tableTag = mapperDocComment.findTagByName("table");
            if (tableTag != null) {
                PsiDocTagValue valueElement = tableTag.getValueElement();
                if (valueElement != null) {
                    tableName = valueElement.getText();
                }
            }
        }
        if (tableName == null) {
            List<String> selectIds = mapper.getSelectIds();
            Map<String, String> sqls = MybatisToSqlUtils.toSqls(mapper.getText(), selectIds);

            List<SQLExprTableSource> tableSources = new ArrayList<>();
            for (String sql : sqls.values()) {
                if (StringUtils.isBlank(sql)) continue;
                SQLStatement sqlStatement = SQLUtils.parseSingleMysqlStatement(sql);
                sqlStatement.accept(new MySqlASTVisitorAdapter(){
                    @Override
                    public boolean visit(SQLExprTableSource x) {
                        tableSources.add(x);
                        return true;
                    }
                });
            }

            List<String> tableFullNames = tableSources.stream().map(x -> x.getExpr().toString()).collect(Collectors.toList());
            Map<String, List<String>> tableFullNameMap = tableFullNames.stream().collect(Collectors.groupingBy(x -> x));
            List<Map<String, Object>> sorted = new ArrayList<>();
            for (String table : tableFullNameMap.keySet()) {
                Map<String, Object> obj = new HashMap<>();
                List<String> names = tableFullNameMap.get(table);
                obj.put("size",names.size());
                obj.put("name",table);
                sorted.add(obj);
            }
            Map<String, Object> first = sorted.stream().max(Comparator.comparingInt(y-> (int) y.get("size"))).orElse(null);
            if (first != null){
                String name = (String)first.get("name");
                tableName = name;
            }else {
                throw new RuntimeException("please add '/** @table schema.table */' in doc comment.");
            }
        }

        if (mapperXmlFile == null) return false;
        Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
        if (xmldoc != null) {

            int insertPos = xmldoc.getText().indexOf("</mapper>");
            PerformanceUtil.mark("genMapperXmlFragment_9");
            StringBuilder insertSql = new StringBuilder();
            insertSql.append("insert into ");
            insertSql.append(tableName);
            insertSql.append(" ( ");
            @NotNull PsiField[] fields = argumentClass.getFields();
            List<String> columns = new ArrayList<>();
            for (PsiField allField : fields) {
                if (allField.getModifierList().hasModifierProperty("static")){
                    continue;
                }
                String column = StringUtil.humpToLine(allField.getName());
                columns.add(column);
            }
            insertSql.append(String.join(",", columns));
            insertSql.append(" ) ");
            insertSql.append("\n VALUES \n");
            List<String> fieldNameFragments = new ArrayList<>();
            for (PsiField allField : fields) {
                if (allField.getModifierList().hasModifierProperty("static")){
                    continue;
                }
                String fieldNameFragment = String.format("${item.%s}", allField.getName());
                fieldNameFragments.add(fieldNameFragment);
            }
            if (isList){
                insertSql.append("<foreach collection=\""+argName+"\" item=\"item\" separator=\",\" open=\"(\" close=\")\">\n");
                insertSql.append(String.join(",", fieldNameFragments));
                insertSql.append("</foreach>");
            }else{
                insertSql.append(" ( ");
                insertSql.append(String.join(",", fieldNameFragments));
                insertSql.append(" ) ");
            }
            String sql = "\n" + String.format("<insert id=\"%s\">\n%s\n</insert>", methodName, insertSql) + "\n";
            PerformanceUtil.mark("genMapperXmlFragment_10");
            xmldoc.insertString(insertPos, sql);
            PerformanceUtil.mark("genMapperXmlFragment_11");
            PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
        }
        PerformanceUtil.mark("genMapperXmlFragment_4");
        return true;
    }

    private boolean genMapperFragment2(Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, PsiClass argumentClass, String argName, boolean isList) {
        int start = document.getText().lastIndexOf("}");
        String fragment = String.format("    void %s(@Param(\"%s\") %s %s);\n", methodName, argName, isList ? String.format("List<%s>", argumentClass.getName()):argumentClass.getName(), argName);
        document.insertString(start, fragment);
        addEntityImport(project, document, containingClass, argumentClass.getName(), argumentClass.getQualifiedName(), isList);
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



    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {

        try {
            PsiClass psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getMethodExpression().getQualifier()).resolve()).getType()).resolve();

            String methodName = ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getMethodExpression().getLastChild().getText();
            if (methodName.startsWith("insert")){
                PsiType argType = ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getArgumentList().getExpressionTypes()[0];
                PsiClass argumentPsiClass;
                if (argType instanceof PsiClassReferenceType ){
                    argumentPsiClass = ((PsiClassReferenceType)argType ).resolve();
                }
                if (argType instanceof PsiClassType){
                    PsiClass type = ((PsiClassType)argType).resolve();
                    if (StringUtils.equals(type.getName(), "List")){
                        argumentPsiClass = ((PsiClassType) ((PsiClassType) ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getArgumentList().getExpressions()[0].getType()).getParameters()[0]).resolve();
                    }
                }
                String argName = ((PsiMethodCallExpression) element.getPrevSibling().getFirstChild()).getArgumentList().getExpressions()[0].getText();
                if (isMapperClass(psiClass)) {
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "generate mybatis fragment for insert";
    }

    @NotNull
    @Override
    public String getText() {
        return "generate mybatis fragment for insert";
    }
}
