package com.beyond.generator.action;

import com.beyond.gen.freemarker.FragmentGenUtils;
import com.beyond.generator.Column;
import com.beyond.generator.dom.IdDomElement;
import com.beyond.generator.dom.Mapper;
import com.beyond.generator.ui.JdbcForm;
import com.beyond.generator.ui.MsgDialog;
import com.beyond.generator.utils.MapperUtil;
import com.beyond.generator.utils.PerformanceUtil;
import com.beyond.generator.utils.PsiDocumentUtils;
import com.beyond.generator.utils.PsiElementUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatternUtil;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.beyond.generator.utils.MapperUtil.*;
import static com.beyond.generator.utils.PropertyUtil.*;
import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * generate mybatis result map
 * @author chenshipeng
 * @date 2022/11/08
 */
public class GenerateMybatisResultMapAction extends GenerateMyBatisBaseAction {
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

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
        Mapper mapper = findMapperXmlByName(project, qualifiedName);
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
            Optional<IdDomElement> sqlOptional = mapper.getSqls().stream().filter(x -> StringUtils.equals(x.getId().getValue(), "Base_Column_List")).findFirst();
            Optional<IdDomElement> resultMapOptional = mapper.getResultMaps().stream().filter(x -> StringUtils.equals(x.getId().getValue(), "BaseResultMap")).findFirst();

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
