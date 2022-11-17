package com.beyond.generator.action;

import com.beyond.gen.freemarker.FragmentGenUtils;
import com.beyond.generator.ui.MsgDialog;
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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author chenshipeng
 * @date 2022/11/08
 */
public class GenerateMybatisFragmentAction extends PsiElementBaseIntentionAction {
    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);


        PsiFile containingFile = element.getContainingFile();
        Document document = psiDocumentManager.getDocument(containingFile);
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);

        if (containingClass.isInterface()){
            String methodName = getPrevWord(document, editor);
            if (StringUtils.isBlank(methodName)) return;

            gen(project, editor,psiDocumentManager, document, containingClass, methodName);
        }else{
            PsiClass psiClass;
            try {
                String methodName = element.getPrevSibling().getFirstChild().getLastChild().getText();
                psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild().getFirstChild()).resolve()).getType()).resolve();
                if (psiClass != null && psiClass.isInterface() && psiClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null){
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null){
                        gen2(project, psiDocumentManager, classDocument, psiClass, methodName, classDocument.getText().lastIndexOf("}"));
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void gen2(@NotNull Project project, PsiDocumentManager psiDocumentManager,  Document document, PsiClass containingClass, String methodName, int start) {
        String entityName = "";
        PsiDocComment docComment = containingClass.getDocComment();
        if (docComment!= null){
            PsiDocTag tag = docComment.findTagByName("entity");
            if (tag != null){
                PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement != null){
                    entityName = valueElement.getText();
                }
            }
        }

        String paramFragment = FragmentGenUtils.createParamFragment(methodName);
        if (methodName.startsWith("getAll")){
            entityName = String.format("List<%s>", entityName);
        }
        document.insertString(start , "    "+entityName + " ");
        document.insertString(start +  entityName.length() + 5, methodName);
        document.insertString(start +  entityName.length() + 5 + methodName.length(), paramFragment + ";\n");
        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, document);

        genMapperXmlFragment(project,  psiDocumentManager, containingClass,docComment, methodName);
    }

    private void gen(@NotNull Project project, Editor editor, PsiDocumentManager psiDocumentManager,  Document document, PsiClass containingClass, String methodName) {
        String entityName = "";
        PsiDocComment docComment = containingClass.getDocComment();
        if (docComment!= null){
            PsiDocTag tag = docComment.findTagByName("entity");
            if (tag != null){
                PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement != null){
                    entityName = valueElement.getText();
                }
            }
        }

        String paramFragment = FragmentGenUtils.createParamFragment(methodName);
        int start = editor.getSelectionModel().getSelectionEnd() - methodName.length();
        if (methodName.startsWith("getAll")){
            entityName = String.format("List<%s>", entityName);
        }
        document.insertString(start , entityName + " ");
        document.insertString(start +  methodName.length() + entityName.length() + 1, paramFragment + ";");
        int newEnd = start +  methodName.length() + entityName.length() + 1 + paramFragment.length() + 1;
        editor.getSelectionModel().setSelection(newEnd, newEnd);
        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, document);

        genMapperXmlFragment(project,  psiDocumentManager, containingClass,docComment, methodName);
    }

    private void genMapperXmlFragment(@NotNull Project project,  PsiDocumentManager psiDocumentManager, PsiClass mapperClass, PsiDocComment mapperDocComment, String methodName) {

        String qualifiedName = mapperClass.getQualifiedName();
        VirtualFile mapperXmlFile = findMapperXmlByName(qualifiedName, ProjectUtil.guessProjectDir(project));

        String tableName = "xxxxxx";
        if (mapperDocComment!= null){
            PsiDocTag tableTag = mapperDocComment.findTagByName("table");
            if (tableTag != null){
                PsiDocTagValue valueElement = tableTag.getValueElement();
                if (valueElement != null){
                    tableName = valueElement.getText();
                }
            }
        }

        try {
            Document xmldoc = FileDocumentManager.getInstance().getDocument(mapperXmlFile);
            if (xmldoc != null){
                String xml = xmldoc.getText();

                try {
                    SAXBuilder sb = new SAXBuilder();
                    StringReader xmlStringReader = new StringReader(xmldoc.getText());
                    org.jdom.Document doc = sb.build(xmlStringReader);
                    Element root = doc.getRootElement();
                    List<Attribute> idAttrs = (List<Attribute>) XPath.selectNodes(root, "//mapper/select/@id");

                    if (idAttrs != null && !idAttrs.isEmpty()) {
                        Attribute found = null;
                        for (Attribute idAttr : idAttrs) {
                            String id = idAttr.getValue();
                            if (StringUtils.equals(id, methodName)) {
                                found = idAttr;
                                break;
                            }
                        }
                        if (found == null) {
                            int insertPos = xml.indexOf("</mapper>");
                            String sql = "\n" + FragmentGenUtils.createXmlFragment(methodName, tableName) + "\n";
                            xmldoc.insertString(insertPos, sql);
                            PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
                        }else{
                            // todo replace
                        }
                    }else{
                        int insertPos = xml.indexOf("</mapper>");
                        String sql = "\n" + FragmentGenUtils.createXmlFragment(methodName, tableName) + "\n";
                        xmldoc.insertString(insertPos, sql);
                        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmldoc);
                    }
                } catch (IOException | JDOMException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            MsgDialog msgDialog = new MsgDialog(project, e.getMessage());
            msgDialog.show();
        }
    }

    private static Map<String, VirtualFile> mapper2xmlMap = new HashMap<>();

    private static VirtualFile findMapperXmlByName(String mapperFullName, VirtualFile root) {
        VirtualFile xmlPath = mapper2xmlMap.get(mapperFullName);
        if (xmlPath != null) {
            if (xmlPath.exists()){
                return xmlPath;
            }else {
                mapper2xmlMap.remove(mapperFullName);
            }
        }
        final VirtualFile[] found = {null};
        VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.isDirectory()) return super.visitFile(file);
                String extension = file.getExtension();
                if (StringUtils.equals(extension, "xml")) {
                    Document xmldoc = FileDocumentManager.getInstance().getDocument(file);
                    if (xmldoc != null){
                        try {
                            SAXBuilder sb = new SAXBuilder();
                            org.jdom.Document doc = sb.build(new StringReader(xmldoc.getText()));
                            Element root = doc.getRootElement();
                            Attribute namespaceText = (Attribute) XPath.selectSingleNode(root, "//mapper/@namespace");

                            if (namespaceText != null) {
                                String namespace = namespaceText.getValue();
                                if (StringUtils.equals(namespace, mapperFullName)) {
                                    found[0] = file;
                                }
                            }
                        } catch (IOException | JDOMException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return super.visitFile(file);
            }
        });
        if (found[0]!= null && found[0].exists()){
            mapper2xmlMap.put(mapperFullName, found[0]);
        }
        return found[0];
    }

    private String getPrevWord(Document document, Editor editor) {
        int selectionEnd = editor.getSelectionModel().getSelectionEnd();
        if (selectionEnd == 0) return null;
        int len = 1;
        while (len < 50) {
            String text = document.getText(TextRange.create(selectionEnd - len, selectionEnd));
            if (text.startsWith(" ") || text.startsWith("\n") || text.startsWith("\r\n") || text.startsWith("\t")) {
                return text.substring(1);
            }
            len++;
        }
        return document.getText(TextRange.create(selectionEnd - len, selectionEnd));
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        String methodName = getPrevWord(editor.getDocument(), editor);
        if (containingClass!=null
                && StringUtils.isNotBlank(methodName)
                && methodName.startsWith("get")
                && containingClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null){
            return true;
        }

        try {
            String methodName2 = element.getPrevSibling().getFirstChild().getLastChild().getText();
            if (StringUtils.isNotBlank(methodName2) && methodName2.startsWith("get")){
                PsiClass psiClass = ((PsiClassReferenceType) ((PsiField) ((PsiReferenceExpression) element.getPrevSibling().getFirstChild().getFirstChild()).resolve()).getType()).resolve();
                if (psiClass != null && psiClass.isInterface() && psiClass.getAnnotation("org.apache.ibatis.annotations.Mapper") != null){
                    VirtualFile classVirtualFile = psiClass.getContainingFile().getVirtualFile();
                    Document classDocument = FileDocumentManager.getInstance().getDocument(classVirtualFile);
                    if (classDocument != null){
                        return true;
                    }
                }
            }
        }catch (Exception ignore){

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
