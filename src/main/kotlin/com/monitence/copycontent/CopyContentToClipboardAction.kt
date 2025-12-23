package com.monitence.copycontent

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection
import java.nio.charset.Charset
import java.util.Locale
import javax.swing.SwingUtilities
import com.intellij.openapi.ide.CopyPasteManager

class CopyContentToClipboardAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = vf != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val root = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Copiando conteúdo...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val text = buildContent(projectBasePath = project.basePath, root = root)

                ApplicationManager.getApplication().invokeLater {
                    CopyPasteManager.getInstance().setContents(StringSelection(text))
                    Messages.showInfoMessage(
                        project,
                        "Conteúdo copiado para a área de transferência.",
                        "Copiar conteúdo"
                    )
                }
            }
        })
    }

    private fun buildContent(projectBasePath: String?, root: VirtualFile): String {
        val sb = StringBuilder()

        fun appendFileHeader(vf: VirtualFile) {
            val path = projectBasePath?.let { base ->
                vf.path.removePrefix(base).trimStart('/', '\\')
            } ?: vf.path
            sb.appendLine("===== $path =====")
        }

        fun isSkippableDir(name: String): Boolean {
            val n = name.lowercase(Locale.ROOT)
            return n == ".git" || n == ".idea" || n == "node_modules" || n == "build" || n == "out" || n == "dist" || n == "target"
        }

        fun isSkippableFile(vf: VirtualFile): Boolean {
            if (vf.isDirectory) return true
            val ft: FileType = vf.fileType
            if (ft.isBinary) return true
            // Evita “bombas” gigantes (ajuste se quiser)
            if (vf.length > 2_000_000) return true // ~2MB
            return false
        }

        if (!root.isDirectory) {
            if (!isSkippableFile(root)) {
                appendFileHeader(root)
                sb.appendLine(readTextBestEffort(root))
                sb.appendLine()
            }
            return sb.toString()
        }

        // Diretório: varre recursivamente
        VfsUtilCore.iterateChildrenRecursively(
            root,
            { vf -> !(vf.isDirectory && isSkippableDir(vf.name)) },
            { vf ->
                if (!isSkippableFile(vf)) {
                    appendFileHeader(vf)
                    sb.appendLine(readTextBestEffort(vf))
                    sb.appendLine()
                }
                true
            }
        )

        return sb.toString()
    }

    private fun readTextBestEffort(vf: VirtualFile): String {
        return try {
            // Detecta charset via IntelliJ quando possível (senão cai no default)
            val bytes = vf.contentsToByteArray()
            String(bytes, Charset.defaultCharset())
        } catch (t: Throwable) {
            "[ERRO ao ler arquivo: ${vf.name}] ${t.message ?: t::class.java.simpleName}"
        }
    }
}
