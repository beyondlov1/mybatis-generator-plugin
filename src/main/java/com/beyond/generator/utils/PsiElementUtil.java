/*
 *  Copyright (c) 2017-2019, bruce.ge.
 *    This program is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU General Public License
 *    as published by the Free Software Foundation; version 2 of
 *    the License.
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *    GNU General Public License for more details.
 *    You should have received a copy of the GNU General Public License
 *    along with this program;
 */

package com.beyond.generator.utils;

import com.beyond.generator.PluginUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Created by bruce.ge on 2016/12/8.
 */
public class PsiElementUtil {
    public static PsiClass getContainingClass(PsiElement psiElement) {
        PsiClass psiClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
        if (psiClass == null) {
            PsiFile containingFile = psiElement.getContainingFile();
            if (containingFile instanceof PsiClassOwner) {
                PsiClass[] classes = ((PsiClassOwner) containingFile).getClasses();
                if (classes.length == 1) {
                    return classes[0];
                }
            }
        }
        return psiClass;
    }

    public static PsiMethod findMethodByFullClassNameAndName(Project project, String fullClassName, String methodName){
        @NotNull PsiMethod[] methods = PsiShortNamesCache.getInstance(project).getMethodsByName(methodName, GlobalSearchScope.projectScope(project));
        for (PsiMethod method : methods) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) continue;
            if (Objects.equals(containingClass.getQualifiedName(), fullClassName)){
                return method;
            }
        }
        return null;
    }

    public static PsiClass findClassByFullClassName(Project project, String fullClassName){
        String shortName = StringUtils.substringAfterLast(fullClassName, ".");
        @NotNull PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(shortName, GlobalSearchScope.projectScope(project));
        for (PsiClass aClass : classes) {
            if (Objects.equals(aClass.getQualifiedName(), fullClassName)){
                return aClass;
            }
        }
        return null;
    }
}
