package com.qingqi.adskip.ui.theme.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * 应用自绘图标库（唯一图标事实源）。
 *
 * 为什么自绘而不用 Material 图标：本应用的品牌图形（启动器/磁贴）是
 * 「HUD 取景框 + 对勾」的科技母题，Material 全家桶的箭头系、纸飞机系
 * 与它不属于同一套语言；图标是出现频率最高、最小可复制的品牌资产，
 * 与启动器割裂的图标会让「同一款应用」的观感碎掉。
 *
 * 设计规范：
 * - 24dp 视窗、2dp 描边、圆头圆角——与 Material line 图标同构，可无缝替换；
 * - 描边/填充统一用黑色占位，实际颜色由 `Icon(tint = …)` 注入
 *   （与 Material 图标相同的约定，保证随主题着色）；
 * - **禁止箭头语素**：不含箭头、纸飞机、chevron。指向性一律由布局与文案表达；
 * - 新增图标前先确认现库没有可复用语义；需要新图标时保持同一套几何语言
 *   （圆角矩形 + 圆弧 + 直线段）。
 */

private val ink = SolidColor(Color.Black)

/** 构建一个 24dp 视窗的自绘图标。 */
private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(block).build()

/** 2dp 圆头描边路径——线稿图标的默认画法。 */
private fun ImageVector.Builder.pathStroke(block: PathBuilder.() -> Unit) = path(
    stroke = ink,
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block,
)

/** 实心填充路径；[fillType] 传 EvenOdd 时后续子路径成为镂空。 */
private fun ImageVector.Builder.pathFill(
    fillType: PathFillType = PathFillType.NonZero,
    block: PathBuilder.() -> Unit,
) = path(
    fill = ink,
    pathFillType = fillType,
    pathBuilder = block,
)

/** 圆角矩形（quadTo 近似圆角，24dp 网格下与真实圆弧不可区分）。 */
private fun PathBuilder.roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    quadTo(x + w, y, x + w, y + r)
    lineTo(x + w, y + h - r)
    quadTo(x + w, y + h, x + w - r, y + h)
    lineTo(x + r, y + h)
    quadTo(x, y + h, x, y + h - r)
    lineTo(x, y + r)
    quadTo(x, y, x + r, y)
    close()
}

object AdSkipIcons {

    // ==================== 导航（描边 + 实心成对） ====================

    /** 首页：屋顶 + 圆角墙体 + 圆角门。 */
    val Home: ImageVector by lazy {
        icon("AdSkip.Home") {
            pathStroke {
                moveTo(4.5f, 10.8f)
                lineTo(12f, 4.4f)
                lineTo(19.5f, 10.8f)
                lineTo(19.5f, 19f)
                quadTo(19.5f, 20.5f, 18f, 20.5f)
                lineTo(6f, 20.5f)
                quadTo(4.5f, 20.5f, 4.5f, 19f)
                close()
            }
            pathStroke {
                moveTo(9.4f, 20.5f)
                lineTo(9.4f, 15.6f)
                quadTo(9.4f, 14.6f, 10.4f, 14.6f)
                lineTo(13.6f, 14.6f)
                quadTo(14.6f, 14.6f, 14.6f, 15.6f)
                lineTo(14.6f, 20.5f)
            }
        }
    }

    /** 首页（实心，导航选中态）。 */
    val HomeFilled: ImageVector by lazy {
        icon("AdSkip.HomeFilled") {
            pathFill(PathFillType.EvenOdd) {
                moveTo(4.5f, 10.8f)
                lineTo(12f, 4.4f)
                lineTo(19.5f, 10.8f)
                lineTo(19.5f, 19f)
                quadTo(19.5f, 20.5f, 18f, 20.5f)
                lineTo(6f, 20.5f)
                quadTo(4.5f, 20.5f, 4.5f, 19f)
                close()
                moveTo(9.4f, 20.5f)
                lineTo(9.4f, 15.6f)
                quadTo(9.4f, 14.6f, 10.4f, 14.6f)
                lineTo(13.6f, 14.6f)
                quadTo(14.6f, 14.6f, 14.6f, 15.6f)
                lineTo(14.6f, 20.5f)
                close()
            }
        }
    }

    /** 应用：2×2 圆角方块网格。 */
    val Apps: ImageVector by lazy {
        icon("AdSkip.Apps") {
            pathStroke {
                roundedRect(4.5f, 4.5f, 7f, 7f, 1.6f)
                roundedRect(12.5f, 4.5f, 7f, 7f, 1.6f)
                roundedRect(4.5f, 12.5f, 7f, 7f, 1.6f)
                roundedRect(12.5f, 12.5f, 7f, 7f, 1.6f)
            }
        }
    }

    /** 应用（实心，导航选中态）。 */
    val AppsFilled: ImageVector by lazy {
        icon("AdSkip.AppsFilled") {
            pathFill {
                roundedRect(4.5f, 4.5f, 7f, 7f, 1.6f)
                roundedRect(12.5f, 4.5f, 7f, 7f, 1.6f)
                roundedRect(4.5f, 12.5f, 7f, 7f, 1.6f)
                roundedRect(12.5f, 12.5f, 7f, 7f, 1.6f)
            }
        }
    }

    /** 记录：文档 + 摘要行（日志/记录的通用符号）。 */
    val Logs: ImageVector by lazy {
        icon("AdSkip.Logs") {
            pathStroke { roundedRect(5f, 3.5f, 14f, 17f, 1.6f) }
            pathStroke {
                moveTo(8.6f, 9f)
                lineTo(15.4f, 9f)
                moveTo(8.6f, 12.4f)
                lineTo(15.4f, 12.4f)
                moveTo(8.6f, 15.8f)
                lineTo(12.6f, 15.8f)
            }
        }
    }

    /** 记录（实心，导航选中态；摘要行镂空）。 */
    val LogsFilled: ImageVector by lazy {
        icon("AdSkip.LogsFilled") {
            pathFill(PathFillType.EvenOdd) {
                roundedRect(5f, 3.5f, 14f, 17f, 1.6f)
                moveTo(8.6f, 8.2f)
                lineTo(15.4f, 8.2f)
                lineTo(15.4f, 9.8f)
                lineTo(8.6f, 9.8f)
                close()
                moveTo(8.6f, 11.6f)
                lineTo(15.4f, 11.6f)
                lineTo(15.4f, 13.2f)
                lineTo(8.6f, 13.2f)
                close()
                moveTo(8.6f, 15f)
                lineTo(12.6f, 15f)
                lineTo(12.6f, 16.6f)
                lineTo(8.6f, 16.6f)
                close()
            }
        }
    }

    /** 我的：头 + 肩线。 */
    val Profile: ImageVector by lazy {
        icon("AdSkip.Profile") {
            pathStroke {
                moveTo(8.9f, 7.6f)
                arcTo(3.1f, 3.1f, 0f, false, false, 15.1f, 7.6f)
                arcTo(3.1f, 3.1f, 0f, false, false, 8.9f, 7.6f)
                close()
            }
            pathStroke {
                moveTo(4.6f, 20.5f)
                curveTo(4.6f, 16.9f, 7.8f, 14.5f, 12f, 14.5f)
                curveTo(16.2f, 14.5f, 19.4f, 16.9f, 19.4f, 20.5f)
            }
        }
    }

    /** 我的（实心，导航选中态）。 */
    val ProfileFilled: ImageVector by lazy {
        icon("AdSkip.ProfileFilled") {
            pathFill {
                moveTo(8.9f, 7.6f)
                arcTo(3.1f, 3.1f, 0f, false, false, 15.1f, 7.6f)
                arcTo(3.1f, 3.1f, 0f, false, false, 8.9f, 7.6f)
                close()
            }
            pathFill {
                moveTo(4.5f, 20.5f)
                curveTo(4.5f, 16.6f, 7.9f, 14.2f, 12f, 14.2f)
                curveTo(16.1f, 14.2f, 19.5f, 16.6f, 19.5f, 20.5f)
                close()
            }
        }
    }

    // ==================== 操作 ====================

    /** 添加（加号）。 */
    val Plus: ImageVector by lazy {
        icon("AdSkip.Plus") {
            pathStroke {
                moveTo(12f, 5.2f)
                lineTo(12f, 18.8f)
                moveTo(5.2f, 12f)
                lineTo(18.8f, 12f)
            }
        }
    }

    /** 关闭/移除（叉号）。 */
    val Close: ImageVector by lazy {
        icon("AdSkip.Close") {
            pathStroke {
                moveTo(6.8f, 6.8f)
                lineTo(17.2f, 17.2f)
                moveTo(17.2f, 6.8f)
                lineTo(6.8f, 17.2f)
            }
        }
    }

    /** 清空（垃圾桶）。 */
    val Delete: ImageVector by lazy {
        icon("AdSkip.Delete") {
            pathStroke {
                moveTo(4.6f, 7f)
                lineTo(19.4f, 7f)
                moveTo(9.6f, 7f)
                lineTo(9.6f, 5.4f)
                quadTo(9.6f, 4.6f, 10.4f, 4.6f)
                lineTo(13.6f, 4.6f)
                quadTo(14.4f, 4.6f, 14.4f, 5.4f)
                lineTo(14.4f, 7f)
                moveTo(6.4f, 7f)
                lineTo(7.1f, 18.9f)
                quadTo(7.2f, 19.6f, 7.9f, 19.6f)
                lineTo(16.1f, 19.6f)
                quadTo(16.8f, 19.6f, 16.9f, 18.9f)
                lineTo(17.6f, 7f)
                moveTo(10.1f, 10.8f)
                lineTo(10.1f, 15.8f)
                moveTo(13.9f, 10.8f)
                lineTo(13.9f, 15.8f)
            }
        }
    }

    /** 分享：三节点互联（无箭头，替代 Material 的 Send 纸飞机）。 */
    val Share: ImageVector by lazy {
        icon("AdSkip.Share") {
            pathStroke {
                moveTo(4.3f, 12f)
                arcTo(2.2f, 2.2f, 0f, false, false, 8.7f, 12f)
                arcTo(2.2f, 2.2f, 0f, false, false, 4.3f, 12f)
                close()
                moveTo(15.3f, 5.8f)
                arcTo(2.2f, 2.2f, 0f, false, false, 19.7f, 5.8f)
                arcTo(2.2f, 2.2f, 0f, false, false, 15.3f, 5.8f)
                close()
                moveTo(15.3f, 18.2f)
                arcTo(2.2f, 2.2f, 0f, false, false, 19.7f, 18.2f)
                arcTo(2.2f, 2.2f, 0f, false, false, 15.3f, 18.2f)
                close()
                moveTo(9.3f, 10.4f)
                lineTo(14.7f, 7.4f)
                moveTo(9.3f, 13.6f)
                lineTo(14.7f, 16.6f)
            }
        }
    }

    /** 警告（三角 + 感叹号）。 */
    val Warning: ImageVector by lazy {
        icon("AdSkip.Warning") {
            pathStroke {
                moveTo(12f, 3.8f)
                lineTo(20.8f, 19.4f)
                lineTo(3.2f, 19.4f)
                close()
                moveTo(12f, 9.6f)
                lineTo(12f, 13.6f)
            }
            pathFill {
                moveTo(12.9f, 16.3f)
                arcTo(0.9f, 0.9f, 0f, false, false, 11.1f, 16.3f)
                arcTo(0.9f, 0.9f, 0f, false, false, 12.9f, 16.3f)
                close()
            }
        }
    }

    /** 搜索（放大镜）。 */
    val Search: ImageVector by lazy {
        icon("AdSkip.Search") {
            pathStroke {
                moveTo(4.9f, 10.7f)
                arcTo(5.8f, 5.8f, 0f, false, false, 16.5f, 10.7f)
                arcTo(5.8f, 5.8f, 0f, false, false, 4.9f, 10.7f)
                close()
                moveTo(15.1f, 15.1f)
                lineTo(19.4f, 19.4f)
            }
        }
    }

    // ==================== 设置行与空态 ====================

    /** 语言/网络（地球）。 */
    val Globe: ImageVector by lazy {
        icon("AdSkip.Globe") {
            pathStroke {
                moveTo(3.6f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 20.4f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 3.6f, 12f)
                close()
                moveTo(12f, 3.6f)
                curveTo(15.9f, 7.4f, 15.9f, 16.6f, 12f, 20.4f)
                curveTo(8.1f, 16.6f, 8.1f, 7.4f, 12f, 3.6f)
                moveTo(3.6f, 12f)
                lineTo(20.4f, 12f)
            }
        }
    }

    /** 云端服务（双层服务架）。 */
    val Server: ImageVector by lazy {
        icon("AdSkip.Server") {
            pathStroke {
                roundedRect(4.4f, 4.2f, 15.2f, 6.6f, 1.5f)
                roundedRect(4.4f, 13.2f, 15.2f, 6.6f, 1.5f)
                moveTo(11.6f, 7.5f)
                lineTo(16.2f, 7.5f)
                moveTo(11.6f, 16.5f)
                lineTo(16.2f, 16.5f)
            }
            pathFill {
                moveTo(8.3f, 7.5f)
                arcTo(0.9f, 0.9f, 0f, false, false, 6.5f, 7.5f)
                arcTo(0.9f, 0.9f, 0f, false, false, 8.3f, 7.5f)
                close()
                moveTo(8.3f, 16.5f)
                arcTo(0.9f, 0.9f, 0f, false, false, 6.5f, 16.5f)
                arcTo(0.9f, 0.9f, 0f, false, false, 8.3f, 16.5f)
                close()
            }
        }
    }

    /** 安全（盾牌）。 */
    val Shield: ImageVector by lazy {
        icon("AdSkip.Shield") {
            pathStroke {
                moveTo(12f, 3.6f)
                lineTo(19.6f, 6.4f)
                lineTo(19.6f, 12.1f)
                curveTo(19.6f, 16.4f, 16.5f, 19.7f, 12f, 21f)
                curveTo(7.5f, 19.7f, 4.4f, 16.4f, 4.4f, 12.1f)
                lineTo(4.4f, 6.4f)
                close()
            }
        }
    }

    /** 计划（时钟）。 */
    val Clock: ImageVector by lazy {
        icon("AdSkip.Clock") {
            pathStroke {
                moveTo(3.6f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 20.4f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 3.6f, 12f)
                close()
                moveTo(12f, 7.4f)
                lineTo(12f, 12.4f)
                lineTo(15.4f, 14.2f)
            }
        }
    }

    /** 保活（心电脉冲）。 */
    val Activity: ImageVector by lazy {
        icon("AdSkip.Activity") {
            pathStroke {
                moveTo(3.4f, 12.6f)
                lineTo(7.6f, 12.6f)
                lineTo(9.6f, 6.8f)
                lineTo(12.8f, 17.2f)
                lineTo(14.8f, 12.6f)
                lineTo(20.6f, 12.6f)
            }
        }
    }

    /** 快照（相机）。 */
    val Snapshot: ImageVector by lazy {
        icon("AdSkip.Snapshot") {
            pathStroke {
                moveTo(8.9f, 7.2f)
                lineTo(9.7f, 5.3f)
                quadTo(9.9f, 4.8f, 10.5f, 4.8f)
                lineTo(13.5f, 4.8f)
                quadTo(14.1f, 4.8f, 14.3f, 5.3f)
                lineTo(15.1f, 7.2f)
                roundedRect(3.9f, 7.2f, 16.2f, 12f, 2f)
                moveTo(8.6f, 13.2f)
                arcTo(3.4f, 3.4f, 0f, false, false, 15.4f, 13.2f)
                arcTo(3.4f, 3.4f, 0f, false, false, 8.6f, 13.2f)
                close()
            }
        }
    }

    /** 反馈（对话气泡）。 */
    val Feedback: ImageVector by lazy {
        icon("AdSkip.Feedback") {
            pathStroke {
                roundedRect(4f, 4.8f, 16f, 11.6f, 1.8f)
                moveTo(9.6f, 16.4f)
                lineTo(7.8f, 19.6f)
                lineTo(7.8f, 16.4f)
            }
        }
    }

    /** 免打扰（月牙，开口朝右）。 */
    val Moon: ImageVector by lazy {
        icon("AdSkip.Moon") {
            pathStroke {
                moveTo(12f, 4.4f)
                // 外弧：圆心 (12,12) 的左半圆；内凹弧让月牙开口朝右。
                arcTo(7.7f, 7.7f, 0f, false, false, 12f, 19.6f)
                arcTo(11.1f, 11.1f, 0f, false, false, 12f, 4.4f)
                close()
            }
        }
    }

    /**
     * 暂无记录（HUD 取景框 + 摘要行）。
     *
     * 呼应启动器图标的取景框母题：空态不再是孤零零的一行字，
     * 而是品牌语言的一部分。
     */
    val EmptyLogs: ImageVector by lazy {
        icon("AdSkip.EmptyLogs") {
            pathStroke {
                moveTo(3.9f, 8.2f)
                lineTo(3.9f, 4.9f)
                lineTo(7.2f, 4.9f)
                moveTo(16.8f, 4.9f)
                lineTo(20.1f, 4.9f)
                lineTo(20.1f, 8.2f)
                moveTo(20.1f, 15.8f)
                lineTo(20.1f, 19.1f)
                lineTo(16.8f, 19.1f)
                moveTo(7.2f, 19.1f)
                lineTo(3.9f, 19.1f)
                lineTo(3.9f, 15.8f)
                moveTo(8.3f, 9.4f)
                lineTo(15.7f, 9.4f)
                moveTo(8.3f, 12.2f)
                lineTo(15.7f, 12.2f)
                moveTo(8.3f, 15f)
                lineTo(12.9f, 15f)
            }
        }
    }

    /** 悬浮窗快捷开关（悬浮面板 + 暂停双竖条）。 */
    val Overlay: ImageVector by lazy {
        icon("AdSkip.Overlay") {
            pathStroke {
                roundedRect(3.4f, 5.6f, 17.2f, 12.8f, 2.4f)
            }
            pathStroke {
                moveTo(10f, 9.6f)
                lineTo(10f, 14.4f)
                moveTo(14f, 9.6f)
                lineTo(14f, 14.4f)
            }
        }
    }

    /** 取点准星（HUD 取景框母题的十字 + 中心圆）。 */
    val TapPoint: ImageVector by lazy {
        icon("AdSkip.TapPoint") {
            pathStroke {
                moveTo(12f, 3.6f)
                lineTo(12f, 7.2f)
                moveTo(12f, 16.8f)
                lineTo(12f, 20.4f)
                moveTo(3.6f, 12f)
                lineTo(7.2f, 12f)
                moveTo(16.8f, 12f)
                lineTo(20.4f, 12f)
            }
            pathStroke {
                moveTo(14.8f, 12f)
                arcTo(2.8f, 2.8f, 0f, true, false, 9.2f, 12f)
                arcTo(2.8f, 2.8f, 0f, true, false, 14.8f, 12f)
                close()
            }
        }
    }
}
