package com.beyond.generator.action;

import com.beyond.generator.dom.MapperLite;
import com.beyond.generator.utils.PsiElementUtil;
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
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Optional;

import static com.beyond.generator.utils.MapperUtil.*;

/**
 * generate mybatis result map
 * @author chenshipeng
 * @date 2022/11/08
 */
public class GenerateMybatisResultMapAction extends GenerateMyBatisBaseAction {
    @Override
    public void _invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

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

        String qualifiedName = mapperClass.getQualifiedName();
        MapperLite mapper = findMapperXmlByName(project, qualifiedName);
        VirtualFile mapperXmlFile = toVirtualFile(mapper);

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
                    completeFullEntityName(project, psiDocumentManager, entityFullName, mapperDocComment);
                }
            }
        }

        if (mapperXmlFile == null) return false;
        Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
        if (xmldoc != null) {
            // Base_Column_List and ResultMap
            Optional<String> sqlOptional = mapper.getSqlIds().stream().filter(x -> StringUtils.equals(x, "Base_Column_List")).findFirst();
            Optional<String> resultMapOptional = mapper.getResultMapIds().stream().filter(x -> StringUtils.equals(x, "BaseResultMap")).findFirst();

            boolean isContinue = createXmlResultMapAndColumnList(project, psiDocumentManager, tableFullName, entityFullName, xmldoc, sqlOptional.isPresent(), resultMapOptional.isPresent());
            if (!isContinue) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);

        if(containingClass == null) return false;

        if (containingClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null && PsiTreeUtil.findFirstParent(element, psiElement -> psiElement instanceof PsiDocComment) != null) {
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
