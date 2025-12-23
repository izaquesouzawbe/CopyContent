package com.monitence.copycontent

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogWrapper
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

        ApplicationManager.getApplication().invokeLater {
            when {
                candidates.isEmpty() -> handleNotFound(project, e, fileName, newText)
                candidates.size == 1 -> openDiffAndMaybeApply(project, candidates.first(), newText)
                else -> chooseAmongMany(project, candidates, fileName, newText)
            }
        }
    }

    private fun chooseAmongMany(project: Project, candidates: List<VirtualFile>, fileName: String, newText: String) {
        val ranked = candidates
            .map { vf -> Candidate(vf, similarityPercent(readFileText(vf) ?: "", newText)) }
            .sortedByDescending { it.similarity }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(ranked)
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

    private fun handleNotFound(project: Project, e: AnActionEvent, fileName: String, newText: String) {
        val suggestedDir = suggestDirectory(project, e)
        if (suggestedDir == null) {
            notify(project, "Não consegui determinar um diretório para sugerir criação do arquivo.", NotificationType.ERROR)
            return
        }

        val dialog = CreateFileLocationDialog(project, fileName, suggestedDir.path)
        dialog.show()

        when (dialog.exitCode) {
            DialogWrapper.OK_EXIT_CODE -> {
                val created = createFileIfNeeded(project, suggestedDir, fileName) ?: return
                openDiffAndMaybeApply(project, created, newText, treatAsNewFile = true)
            }

            CreateFileLocationDialog.CHOOSE_FOLDER_EXIT_CODE -> {
                val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                    title = "Escolha a pasta para criar $fileName"
                    description = "Selecione a pasta onde o arquivo será criado."
                }

                val chosenDir = FileChooser.chooseFile(descriptor, project, suggestedDir) ?: return
                val created = createFileIfNeeded(project, chosenDir, fileName) ?: return
                openDiffAndMaybeApply(project, created, newText, treatAsNewFile = true)
            }

            else -> return
        }
    }

    private fun createFileIfNeeded(project: Project, dir: VirtualFile, fileName: String): VirtualFile? {
        if (!dir.isValid || !dir.isDirectory) {
            notify(project, "Diretório inválido para criação: ${dir.path}", NotificationType.ERROR)
            return null
        }

        val existing = dir.findChild(fileName)
        if (existing != null && existing.isValid && !existing.isDirectory) return existing

        var created: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Criar arquivo $fileName", null, Runnable {
            created = dir.createChildData(this, fileName)
        })

        if (created == null) {
            notify(project, "Falha ao criar o arquivo: ${dir.path}/$fileName", NotificationType.ERROR)
        }
        return created
    }

    private fun openDiffAndMaybeApply(project: Project, target: VirtualFile, newText: String, treatAsNewFile: Boolean = false) {
        FileEditorManager.getInstance(project).openFile(target, true, true)

        val oldText = if (treatAsNewFile) "" else (readFileText(target) ?: "")
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

    /**
     * Sugestão de diretório para criação:
     * 1) seleção no Project (se diretório, ele; se arquivo, parent)
     * 2) arquivo aberto no editor (parent)
     * 3) primeiro source root
     * 4) baseDir do projeto
     */
    private fun suggestDirectory(project: Project, e: AnActionEvent): VirtualFile? {
        val selected = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (selected != null && selected.isValid) {
            if (selected.isDirectory) return selected
            selected.parent?.let { return it }
        }

        val editorFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (editorFile != null && editorFile.isValid) {
            editorFile.parent?.let { return it }
        }

        val roots = ProjectRootManager.getInstance(project).contentSourceRoots
        roots.firstOrNull()?.let { return it }

        return project.baseDir
    }

    private fun readClipboardText(): String? {
        val contents = CopyPasteManager.getInstance().contents ?: return null
        return runCatching { contents.getTransferData(DataFlavor.stringFlavor) as String }.getOrNull()
    }

    private fun readFileText(vf: VirtualFile): String? {
        val doc = FileDocumentManager.getInstance().getDocument(vf)
        return doc?.text ?: runCatching { String(vf.contentsToByteArray(), vf.charset) }.getOrNull()
    }

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
