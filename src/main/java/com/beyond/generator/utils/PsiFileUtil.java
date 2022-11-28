package com.beyond.generator.utils;

import com.beyond.gen.freemarker.FragmentGenUtils;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.ThrowableRunnable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author chenshipeng
 * @date 2022/11/28
 */
public class PsiFileUtil {
    public static void createFile(Project project, String absPath, String content){

        try {
            WriteCommandAction.writeCommandAction(project).run((ThrowableRunnable<Throwable>) () -> {
                if (project.getBasePath() == null){
                    return;
                }
                String content2 = content.replace("\r\n", "\n");
                Path path = Paths.get(absPath);
                String fileName = path.getFileName().toString();
                FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
                PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, type, content2);
                VirtualFile dir = VfsUtil.createDirectoryIfMissing(path.getParent().toAbsolutePath().toString());
                if(dir != null){
                    PsiDirectory directory = PsiDirectoryFactory.getInstance(project).createDirectory(dir);
                    directory.add(file);
                }
            });

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    public static void writeFromTemplate(Project project, String absPath, Object object, String tplName) {
        createFile(project, absPath, FragmentGenUtils.fromTemplate(tplName, object));
    }
}
