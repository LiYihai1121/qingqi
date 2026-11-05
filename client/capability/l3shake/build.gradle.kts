plugins {
    id("adskip.android.library")
}

// L3 按 DESIGN-ADS-FILTERING.md §5 与主 APK 分离：独立模块、独立 namespace，
// 不注册任何组件、不被 :app 依赖。本模块只含可单测的判定算法与启停契约，
// 不含注入框架，也不影响非 Root 主线。
android {
    namespace = "com.qingqi.adskip.l3shake"
}

dependencies {
    testImplementation(libs.junit)
}
