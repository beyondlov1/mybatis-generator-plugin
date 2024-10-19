package com.beyond.generator.action;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author chenshipeng
 * @date 2024/10/18
 */
public abstract class MyBaseIntentionAction extends PsiElementBaseIntentionAction {

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        _invoke(project, editor, element);
    }

    protected abstract void _invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException;
}
