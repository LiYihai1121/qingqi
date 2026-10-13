package com.qingqi.adskip.ui

/**
 * 一级路由登记表：底部导航的四个入口（首页/记录/规则/我的）。
 *
 * 契约（ProjectStructureTest「every route is reachable from the bottom navigation」）：
 * 本对象**恰好登记 4 个 `const val` 一级路由**，且必须与 MainActivity 的
 * TopLevelDestinations、NavHost 注册三方一一对应。
 *
 * 二级页面（应用列表、首次引导）不进本登记表：它们没有底部导航入口，
 * 路由常量就近声明在 MainActivity 的接线处（APPS_ROUTE / ONBOARDING_ROUTE）。
 */
object Routes {
    const val HOME = "home"
    const val LOGS = "logs"
    const val RULES = "rules"
    const val PROFILE = "profile"
}
