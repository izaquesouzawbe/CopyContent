package com.monitence.copycontent

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

@Service(Level.PROJECT)
class CopyContentService(private val project: Project) {

    @Volatile
    var panel: CopyContentPanel? = null

    private val buffer = StringBuilder()

    fun showToolWindow() {
        ToolWindowManager.getInstance(project)
            .getToolWindow("Copiar Conteúdo")
            ?.activate(null, true)
    }

    @Synchronized
    fun getText(): String = buffer.toString()

    @Synchronized
    fun clear() {
        buffer.setLength(0)
        panel?.setText("")
    }

    @Synchronized
    fun setText(value: String) {
        buffer.setLength(0)
        buffer.append(value)
        panel?.setText(buffer.toString())
    }

    @Synchronized
    fun appendText(value: String) {
        if (buffer.isNotEmpty() && !endsWithNewline(buffer)) buffer.append('\n')
        buffer.append(value)
        panel?.setText(buffer.toString())
    }

    private fun endsWithNewline(sb: StringBuilder): Boolean =
        sb.isNotEmpty() && sb[sb.length - 1] == '\n'
}
