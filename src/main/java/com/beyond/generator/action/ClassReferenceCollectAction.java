package com.beyond.generator.action;

import com.beyond.generator.ui.CopyableMsgDialog;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * @author chenshipeng
 * @date 2022/11/25
 */
public class ClassReferenceCollectAction extends PsiElementBaseIntentionAction {

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        List<String> classAbsPaths = new ArrayList<>();
        List<PsiClass> result = new ArrayList<>();
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        for (PsiMethod method : containingClass.getMethods()) {
            collectType2(project, method, result);
        }
        for (PsiClass psiClass : result) {
            String absolutePath = VfsUtil.virtualToIoFile(psiClass.getContainingFile().getVirtualFile()).getAbsolutePath();
            classAbsPaths.add(absolutePath);
        }
        CopyableMsgDialog.show(project, String.join("\n",new HashSet<>(classAbsPaths)));
    }

    private void collectType(Project project, PsiClass psiClass, Collection<PsiClass> result){
        Collection<PsiTypeElement> childrenOfType = PsiTreeUtil.findChildrenOfType(psiClass, PsiTypeElement.class);
        for (PsiTypeElement psiTypeElement : childrenOfType) {
            PsiType type = psiTypeElement.getType();
            if (type instanceof PsiClassReferenceType){
                PsiClass childClass = ((PsiClassReferenceType) type).resolve();
                if (childClass != null && PsiManager.getInstance(project).isInProject(childClass)){
                    if (result.contains(childClass)) continue;
                    result.add(childClass);
                    collectType(project,childClass, result);
                }
            }
        }
    }


    private void collectType2(Project project, PsiMethod psiMethod, Collection<PsiClass> result){
        if (psiMethod == null) return;
        PsiType returnType = psiMethod.getReturnType();
        addProjectPsiClass(project, returnType, result);
        @NotNull PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        for (PsiParameter parameter : parameters) {
            PsiType type = parameter.getType();
            addProjectPsiClass(project, type, result);
        }
        Collection<PsiTypeElement> psiTypeElements = PsiTreeUtil.findChildrenOfType(psiMethod, PsiTypeElement.class);
        for (PsiTypeElement psiTypeElement : psiTypeElements) {
            PsiType type = psiTypeElement.getType();
            addProjectPsiClass(project, type, result);
        }
        Collection<PsiMethodCallExpression> childrenOfType = PsiTreeUtil.findChildrenOfType(psiMethod, PsiMethodCallExpression.class);
        for (PsiMethodCallExpression element : childrenOfType) {
            PsiReferenceExpression methodExpression = element.getMethodExpression();
            PsiExpression psiTypeElement = methodExpression.getQualifierExpression();
            if (psiTypeElement == null) continue;
            PsiType type = psiTypeElement.getType();
            addProjectPsiClass(project, type, result);
            collectType2(project,element.resolveMethod(), result);
        }
    }

    private void addProjectPsiClass(Project project, PsiType type, Collection<PsiClass> result){
        if (type instanceof PsiClassReferenceType){
            PsiClass psiClass = ((PsiClassReferenceType) type).resolve();
            if (psiClass != null && PsiManager.getInstance(project).isInProject(psiClass)){
                if (!result.contains(psiClass)) {
                    result.add(psiClass);
                }
            }
            @NotNull PsiType[] parameters = ((PsiClassReferenceType) type).getParameters();
            if (parameters.length > 0){
                for (PsiType parameter : parameters) {
                    addProjectPsiClass(project, parameter, result);
                }
            }
        }
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        return containingClass != null;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "class reference collect";
    }

    @NotNull
    @Override
    public String getText() {
        return "class reference collect";
    }
}
