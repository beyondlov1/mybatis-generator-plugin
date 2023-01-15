package com.beyond.generator.utils;

/**
 * @author chenshipeng
 * @date 2023/01/15
 */
public class PerformanceUtil {
    public static void mark(String msg){
        long l = System.currentTimeMillis();
        System.out.println(msg+":"+l);
    }
}
