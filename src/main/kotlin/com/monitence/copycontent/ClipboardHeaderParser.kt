package com.monitence.copycontent

data class ClipboardHeaderParseResult(
    val fileName: String,
    val contentWithoutHeader: String
)

object ClipboardHeaderParser {


    fun parseFileNameAndStripHeader(raw: String): ClipboardHeaderParseResult? {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList()

// encontra primeira linha não vazia
        var idx = lines.indexOfFirst { it.trim().isNotEmpty() }
        if (idx == -1) return null

        val first = lines[idx]
        val fileName = extractFileNameFromHeaderLine(first) ?: return null

// remove a linha do header
        lines.removeAt(idx)

// remove também uma linha em branco imediatamente depois (se existir)
        while (idx < lines.size && lines[idx].trim().isEmpty()) {
            lines.removeAt(idx)
        }

        return ClipboardHeaderParseResult(
            fileName = fileName,
            contentWithoutHeader = lines.joinToString("\n")
        )
    }

    private fun extractFileNameFromHeaderLine(line: String): String? {
        var s = line.trim()

// tira prefixos comuns de comentário
        s = s.removePrefix("//").trim()
        s = s.removePrefix("#").trim()
        s = s.removePrefix("/*").trim()
        s = s.removePrefix("*").trim()
        s = s.removeSuffix("*/").trim()

// aceita somente "nome.ext" (sem barras)
// ex.: SigninRequest.java, login.component.ts, foo_bar.py, a-b.tsx
        val m = Regex("""^([A-Za-z0-9._-]+\.[A-Za-z0-9]+)\s*$""").find(s)
        return m?.groupValues?.get(1)
    }
}
