package com.monitence.copycontent

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.border.EmptyBorder

class CreateFileLocationDialog(
    project: Project,
    private val fileName: String,
    private val suggestedDirPath: String
) : DialogWrapper(project, true) {

    companion object {
        const val CHOOSE_FOLDER_EXIT_CODE: Int = NEXT_USER_EXIT_CODE
    }

    init {
        title = "Arquivo não encontrado"
        setOKButtonText("Criar aqui")
        setCancelButtonText("Cancelar")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(10))).apply {
            border = JBUI.Borders.empty(12)
        }

        val titleLabel = JBLabel("Não encontrei o arquivo \"$fileName\" no projeto.").apply {
            font = font.deriveFont(font.style or Font.BOLD, font.size2D + 1f)
        }

        val info = JBLabel("Escolha onde deseja criar o arquivo:").apply {
            border = EmptyBorder(0, 0, 0, 0)
        }

        val pathArea = JTextArea(suggestedDirPath).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1),
                JBUI.Borders.empty(8)
            )
        }

        val top = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.NORTH)
            add(info, BorderLayout.SOUTH)
        }

        panel.add(top, BorderLayout.NORTH)
        panel.add(pathArea, BorderLayout.CENTER)
        return panel
    }

    override fun createActions(): Array<Action> {
        val chooseFolder = object : DialogWrapperAction("Escolher pasta...") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                close(CHOOSE_FOLDER_EXIT_CODE)
            }
        }
        // ordem: OK (Criar aqui), Escolher pasta..., Cancelar
        return arrayOf(okAction, chooseFolder, cancelAction)
    }
}
