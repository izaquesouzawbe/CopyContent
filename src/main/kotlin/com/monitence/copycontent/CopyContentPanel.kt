package com.monitence.copycontent

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class CopyContentPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val statusLabel = JLabel("Linhas: 0  |  Caracteres: 0").apply {
        border = EmptyBorder(0, 8, 0, 8)
    }

    private val editorField = object : EditorTextField("", project, PlainTextFileType.INSTANCE) {
        override fun createEditor(): EditorEx {
            val editor = super.createEditor()
            editor.settings.isLineNumbersShown = true
            editor.settings.isLineMarkerAreaShown = true
            editor.settings.isFoldingOutlineShown = false
            editor.settings.isWhitespacesShown = false
            editor.settings.isUseSoftWraps = false
            editor.settings.additionalLinesCount = 1
            editor.settings.additionalColumnsCount = 1
            return editor
        }
    }.apply {
        setOneLineMode(false)
        border = JBUI.Borders.empty(6)
    }

    init {
        add(createHeader(), BorderLayout.NORTH)

        val scroll = JBScrollPane(editorField).apply {
            border = JBUI.Borders.empty()
        }
        add(scroll, BorderLayout.CENTER)

        add(createStatusBar(), BorderLayout.SOUTH)

        editorField.document.addDocumentListener(SimpleDocListener { updateStatus() })
        updateStatus()
    }

    private fun createHeader(): JPanel {
        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 1, 0),
                JBUI.Borders.empty(10, 10)
            )
        }

        val title = JLabel("Painel de Conteúdo").apply {
            font = font.deriveFont(font.style or Font.BOLD, font.size2D + 2f)
        }

        val titleBox = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(title, BorderLayout.NORTH)

        }

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false

            val copyBtn = JButton("Copiar tudo").apply {
                addActionListener {
                    CopyPasteManager.getInstance().setContents(StringSelection(editorField.text))
                }
            }

            val clearBtn = JButton("Limpar").apply {
                addActionListener {
                    project.getService(CopyContentService::class.java).clear()
                }
            }

            add(copyBtn)
            add(clearBtn)
        }

        header.add(titleBox, BorderLayout.WEST)
        header.add(actions, BorderLayout.EAST)
        return header
    }

    private fun createStatusBar(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1, 0, 0, 0),
                JBUI.Borders.empty(4, 0)
            )
            add(statusLabel, BorderLayout.WEST)
        }
    }

    fun setText(value: String) {
        editorField.text = value
        updateStatus()
        editorField.editor?.caretModel?.moveToOffset(editorField.text.length)
    }

    private fun updateStatus() {
        val text = editorField.text
        val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
        statusLabel.text = "Linhas: $lines  |  Caracteres: ${text.length}"
    }
}
