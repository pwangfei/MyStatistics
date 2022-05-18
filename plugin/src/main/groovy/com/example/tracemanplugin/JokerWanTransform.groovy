package com.example.tracemanplugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter
import org.objectweb.asm.commons.Method

class JokerWanTransform extends Transform {

    private static Project project
    private static final String NAME = "JokerWanAutoTrack"

    JokerWanTransform(Project project) {
        this.project = project
    }

    /**
     * 用于指明 Transform 的名字，也对应了该 Transform 所代表的 Task 名称
     * 这里应该是：transformClassesWithInjectTransformForxxx
     * @return
     */
    @Override
    String getName() {
        return NAME
    }

    /**
     * 需要处理的数据类型，有两种枚举类型
     * CLASSES 代表处理的 java 的 class 文件，RESOURCES 代表要处理 java 的资源
     * @return
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    /**
     * 指 Transform 要操作内容的范围，官方文档 Scope 有 7 种类型：
     * 1. EXTERNAL_LIBRARIES        只有外部库
     * 2. PROJECT                   只有项目内容
     * 3. PROJECT_LOCAL_DEPS        只有项目的本地依赖(本地jar)
     * 4. PROVIDED_ONLY             只提供本地或远程依赖项
     * 5. SUB_PROJECTS              只有子项目。
     * 6. SUB_PROJECTS_LOCAL_DEPS   只有子项目的本地依赖项(本地jar)。
     * 7. TESTED_CODE               由当前变量(包括依赖项)测试的代码
     * @return
     */
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     *  指明该 Transform 是否支持增量编译
     * @return
     */
    @Override
    boolean isIncremental() {
        return true
    }

    static void printCopyRight() {
        println()
        println("******************************************************************************")
        println("******                                                                  ******")
        println("******                欢迎使用 JokerWanTransform 编译插件               ******")
        println("******                                                                  ******")
        println("******************************************************************************")
        println()
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        printCopyRight()

        //TransformInvocation 来获取输入
        Collection<TransformInput> inputs = transformInvocation.inputs

        //TransformInvocation 来获取输出
        TransformOutputProvider outputProvider = transformInvocation.outputProvider

        //删除之前的输出
        if (outputProvider != null) {
            outputProvider.deleteAll()
        }

        //遍历
        inputs.each { TransformInput input ->
            input.directoryInputs.each { DirectoryInput directoryInput ->
                // 处理源码文件
                processDirectoryInput(directoryInput, outputProvider)
            }

            input.jarInputs.each { JarInput jarInput ->
                // 处理jar
                processJarInput(jarInput, outputProvider)
            }
        }

    }

    void processJarInput(JarInput jarInput, TransformOutputProvider outputProvider) {
        // 重命名输出文件（同目录copyFile会冲突）
        def jarName = jarInput.name
        def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length() - 4)
        }

        File dest = outputProvider.getContentLocation(
                jarName + md5Name,
                jarInput.getContentTypes(),
                jarInput.getScopes(),
                Format.JAR
        )

        // TODO do some transform
        println("jarName:    "+jarName)

        // 将 input 的目录复制到 output 指定目录
        FileUtils.copyFile(jarInput.getFile(), dest)
    }

    void processDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider) {
       // 获取输出目录
        File dest = outputProvider.getContentLocation(
                directoryInput.getName(),
                directoryInput.getContentTypes(),
                directoryInput.getScopes(),
                Format.DIRECTORY
        )

        if (directoryInput.file.isDirectory()) {
            directoryInput.file.eachFileRecurse { File file ->
                processClassFile(file)
            }
        }
        println "[Directory]: ${ directoryInput.getName()}"

        // 将处理后的结果复制到输出目录
        FileUtils.copyDirectory(directoryInput.getFile(), dest)
    }



    private void processClassFile(File file) {
        def name = file.name
        println("processClass:    "+name)
        //过滤掉R文件 BuildConfig 匿名内部类
        if (!name.contains("\$") && name.endsWith(".class") && !name.startsWith("R\$") && !"R.class".equals(name) && !"BuildConfig.class".equals(name)) {
            println("processClassFile:    "+file.absolutePath + "--className--" + file.name)
            ClassReader classReader = new ClassReader(file.bytes)
            ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES)
            MyClassVistor appClassVisitor = new MyClassVistor(Opcodes.ASM7,classWriter)
            classReader.accept(appClassVisitor, ClassReader.EXPAND_FRAMES)
            byte[] code = classWriter.toByteArray()
            FileOutputStream fos = new FileOutputStream(file.parentFile.absolutePath + File.separator + name)
            fos.write(code)
            fos.close()
        }
    }

    /*** 处理class文件的分析回调结果*/
    static class MyClassVistor extends ClassVisitor{
        public MyClassVistor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor myMethodVisitor=super.visitMethod(access, name, descriptor, signature, exceptions);
            System.out.println(name);
            return new MyMethodVisitor(api,myMethodVisitor,access,name,descriptor);
        }
    }

    /*** 正在的插桩处理业务*/
    static class MyMethodVisitor extends AdviceAdapter {
        int s;
        protected MyMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(api, methodVisitor, access, name, descriptor);
        }

        /*** 进入方法*/
        @Override
        protected void onMethodEnter() {
            super.onMethodEnter();
            // 插入 long l=System.currentTimeMillis();
            invokeStatic(Type.getType("Ljava/lang/System;"),new Method("currentTimeMillis","()J"));

            //索引
            s = newLocal(Type.LONG_TYPE);
            //用一个本地变量接受上一步的执行结果
            storeLocal(s);


        }

        /*** 退出方法*/
        @Override
        protected void onMethodExit(int opcode) {
            super.onMethodExit(opcode);
//            long e=System.currentTimeMillis();
//            System.out.println("execute"+(e-l)+"ms");

            // 插入 long l=System.currentTimeMillis();
            invokeStatic(Type.getType("Ljava/lang/System;"),new Method("currentTimeMillis","()J"));
            //索引
            int e = newLocal(Type.LONG_TYPE);
            //用一个本地变量接受上一步的执行结果
            storeLocal(e);
            getStatic(Type.getType("Ljava/lang/System;"),"out",Type.getType("Ljava/io/PrintStream;"));
            newInstance(Type.getType("Ljava/lang/StringBuilder;"));
            dup();
            invokeConstructor(Type.getType("Ljava/lang/StringBuilder;"),new Method("<init>","()V"));
            visitLdcInsn(name+" execute");
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"),new Method("append","(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
            //减法 ,方法参数用的是索引
            loadLocal(e);
            loadLocal(s);
            math(SUB,Type.LONG_TYPE);
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"),new Method("append","(J)Ljava/lang/StringBuilder;"));
            visitLdcInsn("ms.");
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"),new Method("append","(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"),new Method("toString","()Ljava/lang/String;"));
            invokeVirtual(Type.getType("Ljava/io/PrintStream;"),new Method("println","(Ljava/lang/String;)V"));

        }

    }

}