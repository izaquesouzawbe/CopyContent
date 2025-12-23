package com.monitence.copycontent

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

class SimpleDocListener(private val onChange: () -> Unit) : DocumentListener {
    override fun documentChanged(event: DocumentEvent) = onChange()
}
