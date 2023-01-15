# mybatis-generator-plugin
1. 生成mybatis相关文件：entity, mapper, mapperxml
![mybatis-generator-1 3-1](https://user-images.githubusercontent.com/24317435/211126081-2d402dbb-a072-4434-8279-26991d729e99.gif)

2. 从 "getAllByXxxAndXxx/getByXxxAndXxx" 生成方法和xml片段
3. xxx 以 's'/'List' 结尾会生成 in 的sql

![mybatis-generator-1 3-2](https://user-images.githubusercontent.com/24317435/211126097-46e3bc29-baf3-4afa-9312-8a6bc8dbb335.gif)

4. 从sql/mybatissql生成实体类
![mybatis-generator-1 3-3](https://user-images.githubusercontent.com/24317435/211126157-1d3d1592-9d4f-4ea7-982f-7d90f0f9f3a8.gif)
![mybatis-generator-1 3-4](https://user-images.githubusercontent.com/24317435/211126160-1b7536cc-1eba-4a8a-b1bb-0c02bae77f69.gif)

PS: mybatissql会提取 #{} 中的字段作为方法的参数, 所以 foreach 会有些问题

5. 将实体类中的字段注释转化为swagger的注解 (前提是这个类有 lombok @Data 注解, 实验性功能)
![mybatis-generator-1](https://user-images.githubusercontent.com/24317435/203993531-48d70bb0-c8c7-45b1-b430-2e2fb86652bd.gif)


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
2. 内置了mysql驱动, 8.0.31
3. 只支持简单生成
4. 2020.2.4 可用，其他未测试

Fuck MyBatisCodeHelperPro !!!
