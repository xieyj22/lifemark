package com.example.ui

import android.app.Activity
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.utils.HtmlGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    documentId: Int,
    viewModel: DocumentViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val systemDarkMode = isSystemInDarkTheme()
    
    // States from view model
    val titleState by viewModel.currentDocTitle.collectAsState()
    val contentState by viewModel.currentDocContent.collectAsState()
    val tagsState by viewModel.currentDocTags.collectAsState()
    val isSavingState by viewModel.isSaving.collectAsState()

    // Tab state (0: 编辑, 1: 预览/阅读)
    var activeTab by remember { mutableIntStateOf(0) }
    
    // Local text input with cursor index tracking
    var contentInput by remember { mutableStateOf(TextFieldValue(contentState)) }
    var titleInput by remember { mutableStateOf(titleState) }
    var tagsInput by remember { mutableStateOf(tagsState) }

    // Keep WebView reference for Print Layout exports
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // Keep a check to ensure we only load once from DB
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId) { _ ->
            // Update local fields when document is successfully read from DB
            contentInput = TextFieldValue(viewModel.currentDocContent.value)
            titleInput = viewModel.currentDocTitle.value
            tagsInput = viewModel.currentDocTags.value
        }
    }

    // Capture system back buttons to trigger autosaves
    val handleBackPress = {
        viewModel.updateDocTitle(titleInput)
        viewModel.updateDocContent(contentInput.text)
        viewModel.updateDocTags(tagsInput)
        
        viewModel.saveActiveDocument {
            onNavigateBack()
        }
    }
    BackHandler(onBack = { handleBackPress() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (titleInput.isBlank()) "未命名文档" else titleInput,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val words = contentInput.text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                            val chars = contentInput.text.length
                            Text(
                                text = "字数: $words | 字符: $chars",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "保存并返回")
                    }
                },
                actions = {
                    // Quick edit save button
                    if (isSavingState) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            viewModel.updateDocTitle(titleInput)
                            viewModel.updateDocContent(contentInput.text)
                            viewModel.updateDocTags(tagsInput)
                            viewModel.saveActiveDocument {
                                Toast.makeText(context, "文档已安全暂存至 SQLite", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "存盘")
                        }
                    }

                    // Share & Export menu expanding options
                    var exportMenuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { exportMenuExpanded = true }) {
                        Icon(Icons.Default.Share, contentDescription = "导出与分享")
                    }

                    DropdownMenu(
                        expanded = exportMenuExpanded,
                        onDismissRequest = { exportMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("分享为 Markdown 源码") },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                            onClick = {
                                exportMenuExpanded = false
                                viewModel.shareMarkdown(context, titleInput, contentInput.text)
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("分享为 HTML 网页") },
                            leadingIcon = { Icon(Icons.Default.Html, contentDescription = null) },
                            onClick = {
                                exportMenuExpanded = false
                                viewModel.shareHtml(context, titleInput, contentInput.text)
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("导出为 PDF / 打印") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                exportMenuExpanded = false
                                if (activeTab != 2 && activeTab != 1) {
                                    Toast.makeText(context, "正在为您自动切换至阅读视图加载公式组件...", Toast.LENGTH_SHORT).show()
                                    activeTab = 2
                                }
                                // Small delay to guarantee WebView loaded resources
                                webViewRef?.postDelayed({
                                    viewModel.printPdf(context, webViewRef, titleInput)
                                }, 500)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("导出为 Word 文档 (.docx)") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = {
                                exportMenuExpanded = false
                                viewModel.exportToDocx(context, titleInput, contentInput.text)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("导出为 PNG 图片") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                exportMenuExpanded = false
                                if (activeTab != 1 && activeTab != 2) {
                                    Toast.makeText(context, "正在为您自动切换至实时分屏加载公式组件...", Toast.LENGTH_SHORT).show()
                                    activeTab = 1
                                }
                                // Small delay to guarantee WebView loaded resources
                                webViewRef?.postDelayed({
                                    viewModel.exportToPng(context, webViewRef, titleInput)
                                }, 600)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            
            // Mode Select Segment Tab Headers (Edit, Dual Split, and Reading view tabs)
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("编辑模式", fontSize = 13.sp)
                        }
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = {
                        viewModel.updateDocContent(contentInput.text)
                        viewModel.updateDocTitle(titleInput)
                        viewModel.updateDocTags(tagsInput)
                        activeTab = 1
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("实时分屏", fontSize = 13.sp)
                        }
                    }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { 
                        // First update states inside ViewModel, so that Android WebView reads the newest text values
                        viewModel.updateDocContent(contentInput.text)
                        viewModel.updateDocTitle(titleInput)
                        viewModel.updateDocTags(tagsInput)
                        activeTab = 2 
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("阅读预览", fontSize = 13.sp)
                        }
                    }
                )
            }

            // Screen content switcher
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activeTab == 0) {
                    // Editing Interface
                    Column(modifier = Modifier.fillMaxSize()) {
                        
                        // Metadata Inputs (Title & Tags)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = titleInput,
                                onValueChange = { titleInput = it },
                                label = { Text("文档标题") },
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            OutlinedTextField(
                                value = tagsInput,
                                onValueChange = { tagsInput = it },
                                label = { Text("标签 (逗号分隔，如: 物理,数学)") },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        // Main raw Markdown input field
                        OutlinedTextField(
                            value = contentInput,
                            onValueChange = { contentInput = it },
                            placeholder = { Text("开始输入带有 Markdown 和 LaTeX 公式的文字吧...\n比如: 行内公式 \$E=mc^2\$\n块级公式:\n\$\$\\int e^x \\, \\mathrm{d}x = e^x + C\$\$\n\n输入后点击顶部的「实时分屏」可以极速查看排版公式渲染！") },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        )

                        // Selection/Symbol Helper Toolbar above virtual keyboard
                        FormattingHelperBar(
                            currentValue = contentInput,
                            onValueChange = { contentInput = it }
                        )
                    }
                } else if (activeTab == 1) {
                    // Split screen live preview!
                    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    if (isLandscape) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left side: Editor
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                OutlinedTextField(
                                    value = contentInput,
                                    onValueChange = { contentInput = it },
                                    placeholder = { Text("在这里输入 Markdown/LaTeX...") },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                )
                                FormattingHelperBar(
                                    currentValue = contentInput,
                                    onValueChange = { contentInput = it }
                                )
                            }
                            
                            // Split Divider Line
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            )

                            // Right side: Webview Live Preview
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                            ) {
                                MarkdownLaTeXWebView(
                                    markdownText = contentInput.text,
                                    isDarkMode = systemDarkMode,
                                    onCreated = { webViewRef = it }
                                )
                            }
                        }
                    } else {
                        // Vertical split-screen
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top: Editor
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = contentInput,
                                    onValueChange = { contentInput = it },
                                    placeholder = { Text("在这里输入 Markdown/LaTeX...") },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                )
                                FormattingHelperBar(
                                    currentValue = contentInput,
                                    onValueChange = { contentInput = it }
                                )
                            }
                            
                            // Split horizontal divider line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            )

                            // Bottom: Web Preview
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                            ) {
                                MarkdownLaTeXWebView(
                                    markdownText = contentInput.text,
                                    isDarkMode = systemDarkMode,
                                    onCreated = { webViewRef = it }
                                )
                            }
                        }
                    }
                } else {
                    // Render HTML full reader preview
                    MarkdownLaTeXWebView(
                        markdownText = contentInput.text,
                        isDarkMode = systemDarkMode,
                        onCreated = { webViewRef = it }
                    )
                }
            }
        }
    }
}

/**
 * JS interface bridge wrapper object to prevent closure capturing issues
 */
class WebAppInterface(var markdownText: String) {
    @android.webkit.JavascriptInterface
    fun getMarkdown(): String {
        return markdownText
    }
}

/**
 * Fast web engine displaying LaTeX math equations
 */
@Composable
fun MarkdownLaTeXWebView(
    markdownText: String,
    isDarkMode: Boolean,
    onCreated: (WebView) -> Unit
) {
    // Dynamic mutable Javascript bridge interface that stays up-to-date
    val jsInterface = remember { WebAppInterface(markdownText) }
    
    // Always update dynamic string reference fresh on recomposition
    LaunchedEffect(markdownText) {
        jsInterface.markdownText = markdownText
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Trigger render on first page finish
                        view?.evaluateJavascript("javascript:renderMarkdown();", null)
                    }
                }
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                
                // Mount security bridge using the stable reference
                addJavascriptInterface(jsInterface, "AndroidInterface")

                loadDataWithBaseURL(
                    "https://local.markdown",
                    HtmlGenerator.generateHtml(isDarkMode),
                    "text/html",
                    "UTF-8",
                    null
                )
                onCreated(this)
            }
        },
        update = { webView ->
            // Keep the jsInterface value fresh and run JS renderMarkdown
            jsInterface.markdownText = markdownText
            webView.evaluateJavascript("javascript:renderMarkdown();", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Shortcut insertion keys
 */
@Composable
fun FormattingHelperBar(
    currentValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val shortcuts = listOf(
        ShortcutOption("H1", "# ", ""),
        ShortcutOption("H2", "## ", ""),
        ShortcutOption("H3", "### ", ""),
        ShortcutOption("加粗", "**", "**"),
        ShortcutOption("斜体", "*", "*"),
        ShortcutOption("内联 LaTeX", "$", "$"),
        ShortcutOption("块级 LaTeX", "$$\n", "\n$$"),
        ShortcutOption("代码", "`", "`"),
        ShortcutOption("代码块", "```kotlin\n", "\n```"),
        ShortcutOption("表格", "\n| 标题 | 标题 |\n|---|---|\n| 内容 | 内容 |\n", ""),
        ShortcutOption("引用", "> ", ""),
        ShortcutOption("链接", "[", "](url)"),
        ShortcutOption("分割线", "\n---\n", ""),
        ShortcutOption("列表", "* ", "")
    )

    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shortcuts.forEach { item ->
                AssistChip(
                    onClick = {
                        val newValue = insertShortcut(currentValue, item.prefix, item.suffix)
                        onValueChange(newValue)
                    },
                    label = { 
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        ) 
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

data class ShortcutOption(
    val label: String,
    val prefix: String,
    val suffix: String
)

/**
 * Perform target selection-replacement operation
 */
fun insertShortcut(currentValue: TextFieldValue, prefix: String, suffix: String): TextFieldValue {
    val oldText = currentValue.text
    val sel = currentValue.selection
    val start = sel.start
    val end = sel.end

    val selectedText = oldText.substring(start, end)
    val totalInsert = prefix + selectedText + suffix
    val newText = oldText.replaceRange(start, end, totalInsert)

    val updatedCursorPos = if (start == end) {
        start + prefix.length
    } else {
        start + prefix.length + selectedText.length + suffix.length
    }

    return TextFieldValue(
        text = newText,
        selection = androidx.compose.ui.text.TextRange(updatedCursorPos)
    )
}
