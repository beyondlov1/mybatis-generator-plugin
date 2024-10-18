package com.beyond.generator.action;

import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author chenshipeng
 * @date 2022/11/25
 */
public class Comment2SwaggerAnnotationAction extends MyBaseIntentionAction {

    public void _invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        PsiJavaParserFacade psiJavaParserFacade = new PsiJavaParserFacadeImpl(project);
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        @NotNull PsiField[] allFields = containingClass.getAllFields();
        for (PsiField psiField : allFields) {
            PsiDocComment docComment = psiField.getDocComment();
            if (docComment != null){
                addImportIfNotExists(project, containingClass, "io.swagger.annotations.ApiModelProperty");
                final @NotNull PsiElement[] descriptionElements = docComment.getDescriptionElements();
                List<String> descriptionLines = new ArrayList<>();
                for (PsiElement descriptionElement : descriptionElements) {
                    final String text = descriptionElement.getText();
                    if (StringUtils.isNotBlank(text)){
                        descriptionLines.add(text.trim());
                    }
                }
                final String description = String.join(";", descriptionLines);
                docComment.delete();
                final PsiAnnotation anno = psiJavaParserFacade.createAnnotationFromText(String.format("@ApiModelProperty(value = \"%s\")", description), null);
                psiField.getParent().addBefore(anno, psiField);
            }
        }
        PsiDocumentUtils.commitAndSaveDocument(PsiDocumentManager.getInstance(project), editor.getDocument());
    }

    private void addImportIfNotExists(Project project, PsiClass psiClass, String fullName){
        if (psiClass.getContainingFile() instanceof PsiJavaFile) {
            PsiJavaFile containingFile = (PsiJavaFile) psiClass.getContainingFile();
            PsiImportList importList = containingFile.getImportList();
            if (importList != null) {
                PsiImportStatementBase entityImport = importList.findSingleClassImportStatement(fullName);
                if (entityImport == null) {
                    entityImport = importList.findSingleImportStatement(fullName);
                }
                if (entityImport == null){
                    PsiJavaFile dummyFile = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText("_Dummy_." + JavaFileType.INSTANCE.getDefaultExtension(), JavaFileType.INSTANCE, "import " + fullName + ";");
                    @NotNull PsiImportStatementBase importStatement = Objects.requireNonNull(Objects.requireNonNull(dummyFile.getImportList()).findSingleClassImportStatement(fullName));
                    importList.add(importStatement);
                }
            }
        }
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        if (containingClass == null) return false;
        final PsiAnnotation annotation = containingClass.getAnnotation("lombok.Data");
        return annotation != null;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "comment to swagger annotation";
    }

    @NotNull
    @Override
    public String getText() {
        return "comment to swagger annotation";
    }
}
