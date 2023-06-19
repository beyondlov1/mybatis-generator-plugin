package com.beyond.generator.action;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.beyond.gen.freemarker.FragmentGenUtils;
import com.beyond.generator.dom.MapperLite;
import com.beyond.generator.utils.MybatisToSqlUtils;
import com.beyond.generator.utils.PerformanceUtil;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.beyond.generator.utils.diff_match_patch;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
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
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.beyond.generator.utils.MapperUtil.*;

/**
 * generate mybatis fragment
 *
 * @author chenshipeng
 * @date 2022/11/08
 */
public class GenerateMybatisFragmentAction extends GenerateMyBatisBaseAction {
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
                msgE(project, e.getMessage());
            }
        } else {
            PsiClass psiClass;
            try {
                PerformanceUtil.mark("invoke1");
                String methodName = element.getPrevSibling().getFirstChild().getLastChild().getText();
                psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild().getFirstChild()).resolve()).getType()).resolve();
                if (isMapperClass(psiClass)) {
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null) {
                        PerformanceUtil.mark("invoke2");
                        gen2(project, psiDocumentManager, classDocument, psiClass, methodName);
                        PerformanceUtil.mark("invoke3");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                msgE(project, e.getMessage());
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

            MapperLite mapper = findMapperXmlByName(project, containingClass.getQualifiedName());
            Map<String, String> resultMapId2TypeMap = mapper.getResultMapId2TypeMap();
            Collection<String> types = resultMapId2TypeMap.values();
            entityName = types.stream().findFirst().orElse(null);

            if (entityName == null){
                List<PsiClass> allDataClass = new ArrayList<>();
                PsiShortNamesCache psiShortNamesCache = PsiShortNamesCache.getInstance(project);
                @NotNull String[] allClassNames = psiShortNamesCache.getAllClassNames();
                for (String allClassName : allClassNames) {
                    @NotNull PsiClass[] psiClasses = psiShortNamesCache.getClassesByName(allClassName, GlobalSearchScope.projectScope(project));
                    allDataClass.addAll(Arrays.asList(psiClasses));
                }
                allDataClass.removeIf(x -> x.getAnnotation("lombok.Data") == null);
                PsiClass matched = allDataClass.stream().min(Comparator.comparingInt(x ->{
                    String name = x.getName();
                    String target = containingClass.getName();
                    diff_match_patch diffMatchPatch = new diff_match_patch();
                    LinkedList<diff_match_patch.Diff> diffs = diffMatchPatch.diff_main(name, target);
                    return diffs.stream().map(y->y.text.length()).reduce(Integer::sum).orElse(0);
                })).orElse(null);
                if (matched != null){
                    entityName = matched.getName();
                }else {
                    throw new RuntimeException("please add '/** @entity entityName */' in doc comment.");
                }
            }
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

        PerformanceUtil.mark("gen2_1");
        boolean isContinue = genMapperXmlFragment(project, psiDocumentManager, containingClass, docComment, methodName);
        PerformanceUtil.mark("gen2_2");
        if (isContinue) {
            PerformanceUtil.mark("gen2_3");
            isContinue = genMapperFragment2(project, psiDocumentManager, document, containingClass, methodName, entityName, fullEntityName);
            PerformanceUtil.mark("gen2_4");
            if (isContinue) {
                msg(project, "Success!");
            }
        }
    }

    private boolean genMapperFragment2(Project project, PsiDocumentManager psiDocumentManager, Document document, PsiClass containingClass, String methodName, String entityName, String fullEntityName) {

        PerformanceUtil.mark("genMapperFragment2_1");
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

        PerformanceUtil.mark("genMapperFragment2_1");

        addEntityImport(project, document, containingClass, entityName, fullEntityName, needList);

        PerformanceUtil.mark("genMapperFragment2_2");


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
        PerformanceUtil.mark("genMapperXmlFragment_1");
        MapperLite mapper = findMapperXmlByName(project, qualifiedName);
        VirtualFile mapperXmlFile = toVirtualFile(mapper);
        PerformanceUtil.mark("genMapperXmlFragment_2");

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

        PerformanceUtil.mark("genMapperXmlFragment_3");

        if (mapperXmlFile == null) return false;
        Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
        if (xmldoc != null) {

            // Base_Column_List and ResultMap
            PerformanceUtil.mark("genMapperXmlFragment_5");
            Optional<String> sqlOptional = mapper.getSqlIds().stream().filter(x -> StringUtils.equals(x, "Base_Column_List")).findFirst();
            Optional<String> resultMapOptional = mapper.getResultMapIds().stream().filter(x -> StringUtils.equals(x, "BaseResultMap")).findFirst();

            boolean isContinue = createXmlResultMapAndColumnList(project, psiDocumentManager, tableName, entityFullName, xmldoc, sqlOptional.isPresent(), resultMapOptional.isPresent());
            PerformanceUtil.mark("genMapperXmlFragment_6");
            if (!isContinue) {
                return false;
            }

            PerformanceUtil.mark("genMapperXmlFragment_7");
            Optional<String> selectOptional = mapper.getSqlIds().stream().filter(x -> StringUtils.equals(x, methodName)).findFirst();
            PerformanceUtil.mark("genMapperXmlFragment_8");

            if (!selectOptional.isPresent()) {
                int insertPos = xmldoc.getText().indexOf("</mapper>");
                PerformanceUtil.mark("genMapperXmlFragment_9");
                String sql = "\n" + FragmentGenUtils.createXmlFragment(methodName, tableName) + "\n";
                PerformanceUtil.mark("genMapperXmlFragment_10");
                xmldoc.insertString(insertPos, sql);
                PerformanceUtil.mark("genMapperXmlFragment_11");
                PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
            } else {
                // todo replace
            }
        }
        PerformanceUtil.mark("genMapperXmlFragment_4");
        return true;
    }


    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        if (containingClass == null) return false;
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
