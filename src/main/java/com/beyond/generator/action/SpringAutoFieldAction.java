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
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author chenshipeng
 * @date 2022/11/25
 */
public class SpringAutoFieldAction extends MyBaseIntentionAction {
    @Override
    public void _invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

        PsiIdentifier identify = PsiTreeUtil.findChildOfType(element.getPrevSibling(), PsiIdentifier.class);
        if (identify == null) return;
        String className =identify.getText();
        PsiJavaParserFacade psiJavaParserFacade = new PsiJavaParserFacadeImpl(project);
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        @NotNull PsiField[] allFields = containingClass.getAllFields();
        for (PsiField psiField : allFields) {
            String name = psiField.getName();
            if (StringUtils.equalsIgnoreCase(className, name)) {
                return;
            }
        }
        PsiAnnotation annotation = containingClass.getAnnotation("lombok.RequiredArgsConstructor");
        if (annotation!= null){
            PsiField newField = psiJavaParserFacade.createFieldFromText(String.format("private final %s %s;", className, StringUtils.uncapitalize(className)), null);
            containingClass.addAfter(newField, containingClass.getLBrace());
        }else{
            PsiField newField = psiJavaParserFacade.createFieldFromText(String.format("private %s %s;", className, StringUtils.uncapitalize(className)), null);
            PsiAnnotation anno = psiJavaParserFacade.createAnnotationFromText("@Autowired", null);
            containingClass.addAfter(newField, containingClass.getLBrace());
            containingClass.addAfter(anno, containingClass.getLBrace());
//            addImportIfNotExists(project, containingClass, "@")
        }
        element.getPrevSibling().replace(psiJavaParserFacade.createExpressionFromText(StringUtils.uncapitalize(className), null));
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
        return containingClass.getAnnotation("org.springframework.web.bind.annotation.RestController") != null
                || containingClass.getAnnotation("org.springframework.stereotype.Service") != null
                || containingClass.getAnnotation("org.springframework.stereotype.Component") != null
                || containingClass.getAnnotation("org.springframework.stereotype.Controller") != null;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "generate spring field";
    }

    @NotNull
    @Override
    public String getText() {
        return "generate spring field";
    }
}
