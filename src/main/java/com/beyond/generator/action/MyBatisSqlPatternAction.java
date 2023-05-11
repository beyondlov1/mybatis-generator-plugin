package com.beyond.generator.action;

import com.beyond.gen.freemarker.JavaEntity;
import com.beyond.gen.freemarker.MapperEntity;
import com.beyond.gen.freemarker.MapperXmlEntity;
import com.beyond.generator.Column;
import com.beyond.generator.PathUtils;
import com.beyond.generator.PluginUtils;
import com.beyond.generator.TypeConverter;
import com.beyond.generator.ui.MsgDialog;
import com.beyond.generator.ui.MyBatisSqlPattern;
import com.beyond.generator.utils.MapperUtil;
import com.beyond.generator.utils.PsiFileUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.ThrowableRunnable;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;

/**
 * @author chenshipeng
 * @date 2021/05/07
 */
public class MyBatisSqlPatternAction extends AnAction {

    public void actionPerformed(@NotNull AnActionEvent event) {

        MyBatisSqlPattern generateForm = new MyBatisSqlPattern(event.getProject());
        generateForm.show(form -> {

            Project project = event.getProject();

            PsiJavaParserFacade psiJavaParserFacade = new PsiJavaParserFacadeImpl(project);
            try {
                String patterns = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("patterns"));
                String annotation = org.apache.commons.lang3.StringUtils.trimToNull(form.getData().get("annotation"));
                List<Pattern> patternList = new ArrayList<>();
                String[] split = patterns.split("\n");
                for (String s : split) {
                    Pattern p = Pattern.compile(s);
                    patternList.add(p);
                }

                Map<PsiMethod, String> allMapperMethod2Sql = MapperUtil.getAllMapperMethod2Sql(project);
                WriteCommandAction.writeCommandAction(project).run((ThrowableRunnable<Throwable>) () -> {
                    for (PsiMethod psiMethod : allMapperMethod2Sql.keySet()) {
                        if (psiMethod == null) continue;
                        String sql = allMapperMethod2Sql.get(psiMethod);
                        for (Pattern pattern : patternList) {
                            Matcher matcher = pattern.matcher(sql.replaceAll("\n", " "));
                            if (matcher.matches()) {
                                if (psiMethod.getAnnotation(annotation) == null && psiMethod.getAnnotation(StringUtils.substringAfterLast(annotation,".")) == null){
                                    psiMethod.getModifierList().add(psiJavaParserFacade.createAnnotationFromText(annotation.trim(), psiMethod));
                                }
                                break;
                            }
                        }
                    }
                });


                form.close(OK_EXIT_CODE);
            } catch (Throwable e) {
                e.printStackTrace();
                MsgDialog msgDialog = new MsgDialog(project, e.getMessage());
                msgDialog.show();
            }

        });

    }


}
