package com.monitence.copycontent

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.SimpleListCellRenderer
import java.awt.datatransfer.DataFlavor
import javax.swing.JList
import kotlin.math.min

class ApplyClipboardByFileNameAction : AnAction("Aplicar Clipboard no arquivo (por nome)"), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val raw = readClipboardText()?.trim()
        if (raw.isNullOrBlank()) {
            notify(project, "Clipboard vazio ou sem texto.", NotificationType.WARNING)
            return
        }

        val parsed = ClipboardHeaderParser.parseFileNameAndStripHeader(raw)
        if (parsed == null) {
            notify(
                project,
                "Não consegui ler o nome do arquivo na primeira linha. Use, por exemplo: \"SigninRequest.java\" (pode estar comentado: // SigninRequest.java).",
                NotificationType.WARNING
            )
            return
        }

        val fileName = parsed.fileName
        val newText = parsed.contentWithoutHeader

        val scope = GlobalSearchScope.projectScope(project)
        val candidates = FilenameIndex.getVirtualFilesByName(project, fileName, scope).toList()

        if (candidates.isEmpty()) {
            notify(project, "Nenhum arquivo encontrado com nome: $fileName", NotificationType.ERROR)
            return
        }

        // UI deve rodar no EDT
        ApplicationManager.getApplication().invokeLater {
            if (candidates.size == 1) {
                openDiffAndMaybeApply(project, candidates.first(), newText)
                return@invokeLater
            }

            // múltiplos arquivos com mesmo nome: oferecer escolha com caminho + similaridade estimada
            val ranked = candidates
                .map { vf -> Candidate(vf, similarityPercent(readFileText(vf) ?: "", newText)) }
                .sortedByDescending { it.similarity }

            val list = ranked

            val popup = JBPopupFactory.getInstance()
                .createPopupChooserBuilder(list)
                .setTitle("Escolha o arquivo para aplicar: $fileName")
                .setRenderer(object : SimpleListCellRenderer<Candidate>() {
                    override fun customize(
                        list: JList<out Candidate>,
                        value: Candidate,
                        index: Int,
                        selected: Boolean,
                        hasFocus: Boolean
                    ) {
                        text = "${value.vf.path}  —  similaridade ~${value.similarity}%"
                    }
                })
                .setItemChosenCallback { chosen ->
                    openDiffAndMaybeApply(project, chosen.vf, newText)
                }
                .createPopup()

            popup.showInFocusCenter()
        }
    }

    private fun openDiffAndMaybeApply(project: Project, target: VirtualFile, newText: String) {
        // abre no editor antes, como você pediu
        FileEditorManager.getInstance(project).openFile(target, true, true)

        val oldText = readFileText(target)
        if (oldText == null) {
            notify(project, "Não foi possível ler o arquivo atual: ${target.path}", NotificationType.ERROR)
            return
        }

        val dialog = ConfirmApplyDiffDialog(project, target, oldText, newText)
        val ok = dialog.showAndGet()
        if (!ok) return

        WriteCommandAction.runWriteCommandAction(project, "Aplicar clipboard em ${target.name}", null, Runnable {
            VfsUtil.saveText(target, newText)
            FileDocumentManager.getInstance().getDocument(target)?.let { doc ->
                FileDocumentManager.getInstance().saveDocument(doc)
            }
        })

        notify(project, "Atualizado: ${target.path}", NotificationType.INFORMATION)
    }

    private fun readClipboardText(): String? {
        val contents = CopyPasteManager.getInstance().contents ?: return null
        return runCatching { contents.getTransferData(DataFlavor.stringFlavor) as String }.getOrNull()
    }

    private fun readFileText(vf: VirtualFile): String? {
        val doc = FileDocumentManager.getInstance().getDocument(vf)
        return doc?.text ?: runCatching { String(vf.contentsToByteArray(), vf.charset) }.getOrNull()
    }

    /**
     * Similaridade simples e barata:
     * - compara linhas na mesma posição (até o menor tamanho)
     * - penaliza diferença de tamanho
     * Retorna 0..100.
     */
    private fun similarityPercent(oldText: String, newText: String): Int {
        val a = oldText.replace("\r\n", "\n").split('\n')
        val b = newText.replace("\r\n", "\n").split('\n')

        val minLen = min(a.size, b.size)
        if (minLen == 0) return 0

        var same = 0
        for (i in 0 until minLen) {
            if (a[i] == b[i]) same++
        }

        val base = (same.toDouble() / minLen.toDouble())
        val sizePenalty = (minLen.toDouble() / maxOf(a.size, b.size).toDouble())
        val score = base * sizePenalty

        return (score * 100.0).toInt().coerceIn(0, 100)
    }

    private data class Candidate(val vf: VirtualFile, val similarity: Int)

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CopyContent Notifications")
            .createNotification(message, type)
            .notify(project)
    }
}
