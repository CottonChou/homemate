package com.caidongzhou.homemate

import android.content.Context
import java.io.File

// 户型文件存储：plans/plan.json，用户在编辑器里改的就是这一份。
object PlanStore {
    fun file(ctx: Context): File = File(ctx.filesDir, "plans/plan.json").apply {
        parentFile?.mkdirs()
        if (!exists()) writeText(Plan.toJson(Plan.demo()))
    }

    fun load(ctx: Context): Plan = Plan.fromJson(file(ctx).readText())

    fun save(ctx: Context, plan: Plan) { file(ctx).writeText(Plan.toJson(plan)) }
}
