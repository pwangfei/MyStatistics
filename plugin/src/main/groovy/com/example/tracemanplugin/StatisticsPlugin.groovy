package com.example.tracemanplugin


import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

class StatisticsPlugin implements Plugin<Project> {
    void apply(Project project) {
        println '*****************MethodTraceMan Plugin apply*********************'
        JiaguConfig jiaguConfig= project.extensions.create("jiagu",  JiaguConfig)//创建扩展配置

        project.afterEvaluate(new Action<Project>() {
            @Override
            void execute(Project p) {
                String username = jiaguConfig.userName
                String password = jiaguConfig.password
                System.out.println(username + "   " + password)
            }
        })



        /**
         * 注册Transform
         */
//        AppExtension appExtension = project.extensions.findByType(AppExtension.class)
//        appExtension.registerTransform(new JokerWanTransform(project))
    }
}