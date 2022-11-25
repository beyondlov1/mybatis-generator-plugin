# mybatis-generator-plugin
生成mybatis相关文件：entity, mapper, mapperxml

![mybatis-generator](https://user-images.githubusercontent.com/24317435/203903213-6d0bef03-6da4-46a8-8ec1-4624451aacae.gif)


### build
```
编译打包依赖 mybatis-generate

https://www.jetbrains.com/idea/download/other.html 下载 2020.2.4

解压到: C:\Users\XXX\.gradle\caches\modules-2\files-2.1\com.jetbrains.intellij.idea\ideaIC\2020.2.4\5685bc139ed2e2c7f1113ac7a9d8f5fc5e3f0334\ideaIC-2020.2.4

修改 build.gradle: 
intellij {
    updateSinceUntilBuild false
    localPath = 'C:\\Users\\Administrator\\.gradle\\caches\\modules-2\\files-2.1\\com.jetbrains.intellij.idea\\ideaIC\\2020.2.4\\5685bc139ed2e2c7f1113ac7a9d8f5fc5e3f0334\\ideaIC-2020.2.4'
}

## 参考: https://blog.csdn.net/q258523454/article/details/120890772


gradle 设置要使用 jdk11
buildPlugin 打包

```

### 限制
1. 只支持mysql
2. 内置了mysql驱动, 5.1.48，只支持5.6之前
3. 只支持简单生成
4. 2020.2.4 可用，其他未测试

Fuck MyBatisCodeHelperPro !!!
