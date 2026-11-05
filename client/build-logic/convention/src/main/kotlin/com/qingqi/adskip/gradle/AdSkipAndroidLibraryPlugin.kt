package com.qingqi.adskip.gradle

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * 约定插件 `adskip.android.library`：能力层独立模块的公共配置。
 *
 * 与 `adskip.android.application` 共享同一套 SDK 版本、Java 兼容性、
 * ktlint 与单测约定，避免 L2/L3 各自粘贴一份配置后随时间漂移。
 * namespace 与依赖留给模块自身（各能力层的包名必须彼此隔离）。
 *
 * 设计依据：docs/planning/DESIGN-BUILD-FRAMEWORK.md 阶段 B、
 * docs/planning/DESIGN-MODULE-REARCH.md ADR-MOD-002。
 */
class AdSkipAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure(LibraryExtension::class.java) {
                compileSdk = 37

                defaultConfig {
                    minSdk = 26
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                lint {
                    checkReleaseBuilds = false
                }

                testOptions {
                    unitTests {
                        isReturnDefaultValues = true
                    }
                }
            }

            extensions.configure<KtlintExtension> {
                android.set(true)
                ignoreFailures.set(false)
            }
        }
    }
}
