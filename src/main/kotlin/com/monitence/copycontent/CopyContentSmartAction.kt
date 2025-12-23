package com.monitence.copycontent

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class CopyContentSmartAction : AnAction("Copiar conteúdo") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private val ignoredDirNames = setOf(".git", ".idea", "node_modules", "dist", "build", "out", "target")
    private val maxFileBytes = 1_000_000
    private val maxTotalChars = 2_000_000

    override fun update(e: AnActionEvent) {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = vf != null && vf.isValid
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val service = project.getService(CopyContentService::class.java)
        service.showToolWindow()

        if (!vf.isDirectory) {
            // ARQUIVO: append
            val text = readFileAsText(project, vf) ?: return
            val payload = buildString {
                appendLine("===== ${vf.path} =====")
                appendLine(text)
                appendLine()
            }
            service.appendText(payload)
            return
        }

        // DIRETÓRIO: varre recursivamente e APPEND no acumulado (mantém histórico)
        object : Task.Backgroundable(project, "Lendo diretório: ${vf.name}", true) {
            override fun run(indicator: ProgressIndicator) {
                val (dirText, skipped) = collectDirectoryText(vf, indicator)

                val payload = buildString {
                    appendLine("##### DIRETÓRIO: ${vf.path} #####")
                    append(dirText)
                    if (skipped.isNotEmpty()) {
                        appendLine()
                        appendLine("----- Ignorados/Pulados -----")
                        skipped.distinct().forEach { appendLine(it) }
                    }
                    appendLine("##### FIM DIRETÓRIO #####")
                    appendLine()
                }

                ApplicationManager.getApplication().invokeLater {
                    project.getService(CopyContentService::class.java).appendText(payload)
                }
            }
        }.queue()
    }

    private fun readFileAsText(project: com.intellij.openapi.project.Project, vf: VirtualFile): String? {
        val doc = FileDocumentManager.getInstance().getDocument(vf)
        val text = doc?.text ?: runCatching { String(vf.contentsToByteArray(), vf.charset) }.getOrNull()
        if (text == null) {
            Messages.showErrorDialog(project, "Não foi possível ler o arquivo como texto.", "Copiar conteúdo")
        }
        return text
    }

    private fun collectDirectoryText(root: VirtualFile, indicator: ProgressIndicator): Pair<String, List<String>> {
        val ftm = FileTypeManager.getInstance()
        val out = StringBuilder()
        val skipped = mutableListOf<String>()
        val stack = ArrayDeque<VirtualFile>()
        stack.add(root)

        while (stack.isNotEmpty() && !indicator.isCanceled) {
            val current = stack.removeLast()

            if (current.isDirectory) {
                if (current != root && ignoredDirNames.contains(current.name)) {
                    skipped.add("${current.path} (diretório ignorado)")
                    continue
                }
                current.children.forEach { stack.add(it) }
            } else {
                val tooLarge = current.length > maxFileBytes
                val binary = ftm.getFileTypeByFile(current).isBinary

                if (tooLarge || binary) {
                    skipped.add("${current.path} (pulado: binário ou grande)")
                    continue
                }

                val content = runCatching { String(current.contentsToByteArray(), current.charset) }.getOrNull()
                if (content == null) {
                    skipped.add("${current.path} (falha ao ler)")
                    continue
                }

                out.appendLine("===== ${current.path} =====")
                out.appendLine(content)
                out.appendLine()

                if (out.length > maxTotalChars) {
                    skipped.add("Interrompido: excedeu limite total (${maxTotalChars} chars).")
                    break
                }
            }
        }

        return out.toString() to skipped
    }
}
