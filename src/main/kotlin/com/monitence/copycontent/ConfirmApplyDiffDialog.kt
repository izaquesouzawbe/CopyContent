package com.monitence.copycontent

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ConfirmApplyDiffDialog(
    private val project: Project,
    private val targetFile: VirtualFile,
    private val oldText: String,
    private val newText: String
) : DialogWrapper(project, true) {

    private val disposable: Disposable = Disposer.newDisposable("CopyContentDiffDialog")
    private val rootPanel = JPanel(BorderLayout())

    init {
        title = "Confirmar atualização: ${targetFile.path}"
        setOKButtonText("Aplicar")
        setCancelButtonText("Cancelar")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val requestPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)

        val psiFile = PsiManager.getInstance(project).findFile(targetFile)
        val fileType = psiFile?.fileType

        val contentFactory = DiffContentFactory.getInstance()
        val left = if (fileType != null) contentFactory.create(project, oldText, fileType) else contentFactory.create(oldText)
        val right = if (fileType != null) contentFactory.create(project, newText, fileType) else contentFactory.create(newText)

        val request = SimpleDiffRequest(
            "Diferenças",
            left,
            right,
            "Atual (${targetFile.path})",
            "Novo (Clipboard)"
        )

        requestPanel.setRequest(request)
        rootPanel.add(requestPanel.component, BorderLayout.CENTER)
        return rootPanel
    }

    override fun dispose() {
        Disposer.dispose(disposable)
        super.dispose()
    }
}
