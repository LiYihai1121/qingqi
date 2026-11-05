plugins {
    id("adskip.android.library")
}

// L2 是设备级网络控制能力，与 L1 的「点击已展示按钮」风险等级不同：
// 独立模块 + 独立 namespace，且刻意不被 :app 依赖（隔离原型，不进发布分支）。
android {
    namespace = "com.qingqi.adskip.l2vpn"
}

dependencies {
    testImplementation(libs.junit)
}
