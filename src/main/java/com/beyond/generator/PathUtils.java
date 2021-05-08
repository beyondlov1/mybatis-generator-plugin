package com.beyond.generator;

import java.nio.file.Paths;

/**
 * @author chenshipeng
 * @date 2021/01/18
 */
public class PathUtils {
    public static String concat(String s, String...ss){
        String result = s;
        for (String as : ss) {
            result = concat(result, as);
        }
        return result;
    }

    private static String concat(String s1, String s2){
        return Paths.get(s1, s2).toString();
    }
}
