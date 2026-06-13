package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Document
import com.example.data.DocumentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentViewModel(private val repository: DocumentRepository) : ViewModel() {

    // Document search query
    val searchQuery = MutableStateFlow("")

    // Raw document list from repository
    private val rawDocuments = repository.allDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered lists of documents by query
    val documentsState: StateFlow<List<Document>> = rawDocuments
        .combine(searchQuery) { docs, query ->
            if (query.isBlank()) {
                docs
            } else {
                docs.filter { doc ->
                    doc.title.contains(query, ignoreCase = true) ||
                    doc.content.contains(query, ignoreCase = true) ||
                    doc.tags.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Temporary reactive buffers for active editing session
    val currentDocId = MutableStateFlow<Int?>(null)
    val currentDocTitle = MutableStateFlow("")
    val currentDocContent = MutableStateFlow("")
    val currentDocTags = MutableStateFlow("")
    val isSaving = MutableStateFlow(false)

    // Set editing context from dynamic targets
    fun loadDocument(id: Int, onLoaded: (Document?) -> Unit = {}) {
        viewModelScope.launch {
            if (id == -1) {
                // Initialize as fresh/unsaved document
                currentDocId.value = null
                currentDocTitle.value = "未命名文档"
                currentDocContent.value = "# 新文档\n\n在此输入 Markdown 内容。\n\n行内公式: ${'$'}E = mc^2${'$'}\n\n多行公式:\n\n${'$'}${'$'}\n\\int_0^1 x^2 \\, \\mathrm{d}x = \\frac{1}{3}\n${'$'}${'$'}"
                currentDocTags.value = ""
                onLoaded(null)
            } else {
                val doc = repository.getDocumentById(id)
                if (doc != null) {
                    currentDocId.value = doc.id
                    currentDocTitle.value = doc.title
                    currentDocContent.value = doc.content
                    currentDocTags.value = doc.tags
                    onLoaded(doc)
                }
            }
        }
    }

    fun updateDocTitle(title: String) {
        currentDocTitle.value = title
    }

    fun updateDocContent(content: String) {
        currentDocContent.value = content
    }

    fun updateDocTags(tags: String) {
        currentDocTags.value = tags
    }

    // Save active document. Will perform insert or update accordingly
    fun saveActiveDocument(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            isSaving.value = true
            val id = currentDocId.value
            val title = currentDocTitle.value.ifBlank { "未命名文档" }
            val content = currentDocContent.value
            val tags = currentDocTags.value

            val docToSave = if (id == null) {
                Document(
                    title = title,
                    content = content,
                    tags = tags,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                val existing = repository.getDocumentById(id)
                existing?.copy(
                    title = title,
                    content = content,
                    tags = tags,
                    updatedAt = System.currentTimeMillis()
                ) ?: Document(
                    id = id,
                    title = title,
                    content = content,
                    tags = tags,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }

            if (id == null) {
                val newId = repository.insert(docToSave).toInt()
                currentDocId.value = newId
                onComplete(newId)
            } else {
                repository.update(docToSave)
                onComplete(id)
            }
            isSaving.value = false
        }
    }

    // Toggle Favorite status
    fun toggleFavorite(document: Document) {
        viewModelScope.launch {
            repository.update(document.copy(isFavorite = !document.isFavorite))
        }
    }

    // Delete a document by ID
    fun deleteDocument(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    // Import multiple Markdown/Text files as new documents
    fun importDocuments(context: Context, uris: List<Uri>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            var successCount = 0
            for (uri in uris) {
                try {
                    // Try to resolve the actual file name
                    var filename: String? = null
                    if (uri.scheme == "content") {
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (index != -1) {
                                    filename = it.getString(index)
                                }
                            }
                        }
                    }
                    if (filename == null) {
                        filename = uri.path?.substringAfterLast('/')
                    }
                    
                    val finalFileName = filename ?: "未命名导入"
                    
                    // Deduct the file extension
                    val title = when {
                        finalFileName.endsWith(".md", ignoreCase = true) -> finalFileName.dropLast(3)
                        finalFileName.endsWith(".markdown", ignoreCase = true) -> finalFileName.dropLast(9)
                        finalFileName.endsWith(".txt", ignoreCase = true) -> finalFileName.dropLast(4)
                        else -> finalFileName
                    }
                    
                    // Read file content
                    val stream = context.contentResolver.openInputStream(uri)
                    val content = stream?.use { inputStream ->
                        inputStream.bufferedReader().use { reader ->
                            reader.readText()
                        }
                    } ?: ""
                    
                    val newDoc = Document(
                        title = title,
                        content = content,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        tags = "已导入"
                    )
                    
                    repository.insert(newDoc)
                    successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onComplete(successCount)
        }
    }

    // Share raw markdown source
    fun shareMarkdown(context: Context, title: String, content: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
            }
            context.startActivity(Intent.createChooser(intent, "分享 Markdown 源码"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Export styled self-contained HTML that prints math nicely
    fun shareHtml(context: Context, title: String, content: String) {
        try {
            val cleanContent = content
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$")

            val bundledHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        line-height: 1.6;
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 24px;
                        color: #212121;
                        background-color: #FFFFFF;
                    }
                    hr { height: 1px; border: 0; background: #E0E0E0; margin: 24px 0; }
                    kbd, code { font-family: monospace; background: #F5F5F5; color: #A31515; padding: 2px 6px; border-radius: 4px; }
                    pre { background: #F5F5F5; padding: 16px; border-radius: 8px; overflow-x: auto; }
                    pre code { background: none; color: inherit; padding: 0; }
                    blockquote { border-left: 4px solid #1A73E8; background: #F8F9FA; padding: 12px 18px; margin: 0 0 16px 0; border-radius: 0 4px 4px 0; }
                    table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }
                    th, td { border: 1px solid #E0E0E0; padding: 8px 12px; text-align: left; }
                    th { background-color: #F8F9FA; }
                    tr:nth-child(even) { background-color: #FAFAFA; }
                    .katex-display { overflow-x: auto; padding: 10px 0; }
                </style>
                <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/contrib/auto-render.min.js"></script>
            </head>
            <body>
                <h1 style="border-bottom: 1px solid #E0E0E0; padding-bottom: 8px;">$title</h1>
                <div id="content">正在解析内容...</div>
                <script>
                    document.addEventListener("DOMContentLoaded", function() {
                        const rawMd = `$cleanContent`;
                        document.getElementById('content').innerHTML = marked.parse(rawMd);
                        
                        renderMathInElement(document.getElementById('content'), {
                            delimiters: [
                                {left: "$$", right: "$$", display: true},
                                {left: "$", right: "$", display: false}
                            ],
                            throwOnError: false
                        });
                    });
                </script>
            </body>
            </html>
            """.trimIndent()

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, bundledHtml)
            }
            context.startActivity(Intent.createChooser(intent, "分享 HTML 网页"))
        } catch (e: Exception) {
            Toast.makeText(context, "生成 HTML 失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Export and Print PDF directly using Android's PrintManager & WebView
    fun printPdf(context: Context, webView: WebView?, title: String) {
        if (webView == null) {
            Toast.makeText(context, "无法获取预览，请在阅读视图下进行打印或 PDF 导出", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            val jobName = "Markdown_PDF_${title}"
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            
            if (printManager != null) {
                printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
            } else {
                Toast.makeText(context, "系统打印服务不可用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: java.lang.Exception) {
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Export to Word document (.docx)
    fun exportToDocx(context: Context, title: String, content: String) {
        try {
            val bytes = generateDocx(title, content)
            val file = File(context.cacheDir, "$title.docx")
            FileOutputStream(file).use { out ->
                out.write(bytes)
            }
            val uri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享 Word 文档 (.docx)"))
        } catch (e: Exception) {
            Toast.makeText(context, "导出 DOCX 失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Export to high rendering PNG layout via capturing WebView Canvas
    fun exportToPng(context: Context, webView: WebView?, title: String) {
        if (webView == null) {
            Toast.makeText(context, "画布未就绪，请在阅读或实时分屏下重试", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            webView.post {
                try {
                    val density = context.resources.displayMetrics.density
                    val contentHeight = (webView.contentHeight * density).toInt().coerceAtLeast(webView.height)
                    val width = webView.width.coerceAtLeast(300)
                    
                    val bitmap = Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    webView.draw(canvas)
                    
                    val file = File(context.cacheDir, "$title.png")
                    FileOutputStream(file).use { outStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                        outStream.flush()
                    }
                    
                    val uri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享 PNG 图片"))
                } catch (inner: Exception) {
                    Toast.makeText(context, "画幅保存失败: ${inner.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "导出图片启动异常: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // OpenXML docx builder Helpers
    private fun generateDocx(title: String, markdownText: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zip ->
            // [Content_Types].xml
            val contentTypesXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
            """.trimIndent()
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(contentTypesXml.toByteArray())
            zip.closeEntry()

            // _rels/.rels
            val relsXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
            """.trimIndent()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(relsXml.toByteArray())
            zip.closeEntry()

            // word/document.xml
            val documentXml = buildDocumentXml(title, markdownText)
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }
        return outputStream.toByteArray()
    }

    private fun buildDocumentXml(title: String, markdownText: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n")
        sb.append("  <w:body>\n")
        
        // Document Title
        sb.append("    <w:p>\n")
        sb.append("      <w:pPr>\n")
        sb.append("        <w:pStyle w:val=\"Title\"/>\n")
        sb.append("        <w:jc w:val=\"center\"/>\n")
        sb.append("        <w:spacing w:before=\"360\" w:after=\"240\"/>\n")
        sb.append("      </w:pPr>\n")
        sb.append("      <w:r>\n")
        sb.append("        <w:rPr>\n")
        sb.append("          <w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>\n")
        sb.append("          <w:b/>\n")
        sb.append("          <w:sz w:val=\"48\"/>\n")
        sb.append("          <w:color w:val=\"2B579A\"/>\n")
        sb.append("        </w:rPr>\n")
        sb.append("        <w:t>").append(escapeXml(title)).append("</w:t>\n")
        sb.append("      </w:r>\n")
        sb.append("    </w:p>\n")
        
        val lines = markdownText.split("\n")
        var inCodeBlock = false
        val codeBlockBuilder = StringBuilder()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    sb.append(formatCodeBlockWord(codeBlockBuilder.toString()))
                    codeBlockBuilder.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }
            
            if (inCodeBlock) {
                codeBlockBuilder.append(line).append("\n")
                continue
            }
            
            if (trimmed.isEmpty()) {
                sb.append("    <w:p/>\n")
                continue
            }
            
            if (trimmed.startsWith("# ")) {
                sb.append(formatHeadingWord(trimmed.substring(2), 36, "2B579A", true))
            } else if (trimmed.startsWith("## ")) {
                sb.append(formatHeadingWord(trimmed.substring(3), 28, "323130", true))
            } else if (trimmed.startsWith("### ")) {
                sb.append(formatHeadingWord(trimmed.substring(4), 24, "555555", true))
            } else if (trimmed.startsWith("#### ")) {
                sb.append(formatHeadingWord(trimmed.substring(5), 20, "777777", true))
            } else if (trimmed.startsWith("> ")) {
                sb.append(formatBlockquoteWord(trimmed.substring(2)))
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                sb.append(formatBulletItemWord(trimmed.substring(2)))
            } else if (trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
                val marker = trimmed.substringBefore(". ") + "."
                val text = trimmed.substringAfter(". ")
                sb.append(formatOrderedItemWord(marker, text))
            } else if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                sb.append("    <w:p>\n")
                sb.append("      <w:pPr>\n")
                sb.append("        <w:pBdr>\n")
                sb.append("          <w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"CCCCCC\"/>\n")
                sb.append("        </w:pBdr>\n")
                sb.append("      </w:pPr>\n")
                sb.append("    </w:p>\n")
            } else {
                sb.append("    <w:p>\n")
                sb.append("      <w:pPr>\n")
                sb.append("        <w:lineSpacing w:line=\"300\" w:lineRule=\"auto\"/>\n")
                sb.append("      </w:pPr>\n")
                sb.append(parseInlineFormatting(line))
                sb.append("    </w:p>\n")
            }
        }
        
        if (inCodeBlock && codeBlockBuilder.isNotEmpty()) {
            sb.append(formatCodeBlockWord(codeBlockBuilder.toString()))
        }
        
        sb.append("  </w:body>\n")
        sb.append("</w:document>\n")
        return sb.toString()
    }

    private fun formatHeadingWord(text: String, sizeVal: Int, colorHex: String, isBold: Boolean): String {
        val boldTag = if (isBold) "<w:b/>" else ""
        return """
            <w:p>
              <w:pPr>
                <w:spacing w:before="240" w:after="120"/>
                <w:keepNext/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                  $boldTag
                  <w:sz w:val="$sizeVal"/>
                  <w:color w:val="$colorHex"/>
                </w:rPr>
                <w:t>${escapeXml(text)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun formatBlockquoteWord(text: String): String {
        return """
            <w:p>
              <w:pPr>
                <w:shd w:fill="F4F4F4" w:val="clear" w:color="auto"/>
                <w:ind w:left="480" w:right="480"/>
                <w:spacing w:before="120" w:after="120"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                  <w:i/>
                  <w:color w:val="555555"/>
                </w:rPr>
                <w:t>${escapeXml(text)}</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun formatBulletItemWord(text: String): String {
        return """
            <w:p>
              <w:pPr>
                <w:ind w:left="360" w:hanging="180"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                </w:rPr>
                <w:t>• </w:t>
              </w:r>
              ${parseInlineFormatting(text)}
            </w:p>
        """.trimIndent()
    }

    private fun formatOrderedItemWord(marker: String, text: String): String {
        return """
            <w:p>
              <w:pPr>
                <w:ind w:left="360" w:hanging="180"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                  <w:b/>
                </w:rPr>
                <w:t>$marker </w:t>
              </w:r>
              ${parseInlineFormatting(text)}
            </w:p>
        """.trimIndent()
    }

    private fun formatCodeBlockWord(code: String): String {
        val sb = StringBuilder()
        val codeLines = code.split("\n")
        for (line in codeLines) {
            if (line.isEmpty() && line == codeLines.last()) continue
            sb.append("    <w:p>\n")
            sb.append("      <w:pPr>\n")
            sb.append("        <w:shd w:fill=\"F5F5F5\" w:val=\"clear\" w:color=\"auto\"/>\n")
            sb.append("        <w:ind w:left=\"480\"/>\n")
            sb.append("        <w:spacing w:before=\"0\" w:after=\"0\"/>\n")
            sb.append("      </w:pPr>\n")
            sb.append("      <w:r>\n")
            sb.append("        <w:rPr>\n")
            sb.append("          <w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>\n")
            sb.append("          <w:sz w:val=\"18\"/>\n")
            sb.append("          <w:color w:val=\"A31515\"/>\n")
            sb.append("        </w:rPr>\n")
            sb.append("        <w:t xml:space=\"preserve\">").append(escapeXml(line)).append("</w:t>\n")
            sb.append("      </w:r>\n")
            sb.append("    </w:p>\n")
        }
        return sb.toString()
    }

    private fun parseInlineFormatting(text: String): String {
        val sb = StringBuilder()
        val currentText = StringBuilder()
        var isBold = false
        var isItalic = false
        var isCode = false
        var i = 0
        
        fun commitRun() {
            if (currentText.isNotEmpty()) {
                val escapedVal = escapeXml(currentText.toString())
                sb.append("<w:r>")
                sb.append("<w:rPr>")
                sb.append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>")
                if (isBold) sb.append("<w:b/>")
                if (isItalic) sb.append("<w:i/>")
                if (isCode) {
                    sb.append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
                    sb.append("<w:color w:val=\"A31515\"/>")
                    sb.append("<w:shd w:fill=\"F5F5F5\" w:val=\"clear\" w:color=\"auto\"/>")
                    sb.append("<w:sz w:val=\"18\"/>")
                } else {
                    sb.append("<w:sz w:val=\"22\"/>")
                }
                sb.append("</w:rPr>")
                sb.append("<w:t xml:space=\"preserve\">").append(escapedVal).append("</w:t>")
                sb.append("</w:r>")
                currentText.clear()
            }
        }
        
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '*' && text[i+1] == '*') {
                commitRun()
                isBold = !isBold
                i += 2
            } else if (text[i] == '*' || text[i] == '_') {
                commitRun()
                isItalic = !isItalic
                i++
            } else if (text[i] == '`') {
                commitRun()
                isCode = !isCode
                i++
            } else {
                currentText.append(text[i])
                i++
            }
        }
        commitRun()
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

class DocumentViewModelFactory(private val repository: DocumentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
