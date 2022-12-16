package com.beyond.generator.action;

import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.generator.PathUtils;
import com.beyond.generator.PluginUtils;
import com.beyond.generator.ui.Callback;
import com.beyond.generator.ui.EntityForm;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.beyond.generator.utils.PsiFileUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.beyond.generator.utils.MapperUtil.*;
import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * @author chenshipeng
 * @date 2022/11/08
 */
public class EntityGenerateAction extends PsiElementBaseIntentionAction {
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);

        try {
            String expr = element.getPrevSibling().getText();

            boolean useCache = !expr.contains(">>");
            String sep = ">>";
            if (useCache){
                sep = ">";
            }
            String[] split = trimSplit(expr,sep);
            if (split.length == 2){
                String entityName = split[1];
                String fieldNamesStr = split[0];
                String[] fieldNames = trimSplit(fieldNamesStr, ",");
                if (fieldNames.length >= 1 && StringUtils.isNotBlank(entityName)){

                    learn(project, useCache);

                    JavaEntity javaEntity = new JavaEntity();

                    List<String> imports = new ArrayList<>();
                    imports.add("lombok.Data");
                    javaEntity.setImports(imports);

                    EntityForm entityForm = new EntityForm(project);
                    entityForm.show(form -> {
                        form.setOk(true);
                        form.close(OK_EXIT_CODE);
                    });
                    if (!entityForm.isOk()) return;

                    String packageName = entityForm.getData().get("packageName");
                    if (packageName == null){
                        return;
                    }
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


                    TextRange textRange = element.getPrevSibling().getTextRange();
                    editor.getDocument().replaceString(textRange.getStartOffset(),textRange.getEndOffset(), String.format("%s %s = new %s();",entityName, StringUtils.uncapitalize(entityName),entityName));
                    PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager,editor.getDocument());

                    addEntityImport(editor.getDocument(), containingClass, packageName+"."+entityName, false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            msg(project, e.getMessage());
        }
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
        String expr = element.getPrevSibling().getText();
        boolean useCache = !expr.contains(">>");
        String sep = ">>";
        if (useCache){
            sep = ">";
        }
        String[] split = expr.split(sep);
        if (split.length == 2){
            String entityName = split[1];
            String fieldNamesStr = split[0];
            String[] fieldNames = StringUtils.split(fieldNamesStr, ",");
            if (fieldNames.length >= 1 && StringUtils.isNotBlank(entityName)){
                return true;
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
