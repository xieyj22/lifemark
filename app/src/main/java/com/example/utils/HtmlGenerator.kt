package com.example.utils

object HtmlGenerator {
    fun generateHtml(isDarkMode: Boolean): String {
        val backgroundColor = if (isDarkMode) "#121212" else "#FFFFFF"
        val textColor = if (isDarkMode) "#E0E0E0" else "#212121"
        val codeBgColor = if (isDarkMode) "#1E1E1E" else "#F5F5F5"
        val codeColor = if (isDarkMode) "#CE9178" else "#A31515"
        val accentColor = if (isDarkMode) "#8AB4F8" else "#1A73E8"
        val blockquoteBg = if (isDarkMode) "#1C1C1E" else "#F8F9FA"
        val blockquoteBorder = if (isDarkMode) "#3A3A3C" else "#E0E0E0"

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <title>Markdown Preview</title>
            
            <!-- KaTeX CSS -->
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css" crossorigin="anonymous">
            
            <!-- GitHub Markdown Style custom-fitted to Theme -->
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji";
                    background-color: $backgroundColor;
                    color: $textColor;
                    line-height: 1.6;
                    padding: 16px;
                    margin: 0;
                    word-wrap: break-word;
                    font-size: 15px;
                }
                
                h1, h2, h3, h4, h5, h6 {
                    color: ${if (isDarkMode) "#FFFFFF" else "#000000"};
                    margin-top: 24px;
                    margin-bottom: 16px;
                    font-weight: 600;
                    line-height: 1.25;
                }
                
                h1 { font-size: 1.8em; border-bottom: 1px solid $blockquoteBorder; padding-bottom: 0.3em; }
                h2 { font-size: 1.4em; border-bottom: 1px solid $blockquoteBorder; padding-bottom: 0.3em; }
                h3 { font-size: 1.2em; }
                h4 { font-size: 1.0em; }
                
                a {
                    color: $accentColor;
                    text-decoration: none;
                }
                a:hover {
                    text-decoration: underline;
                }
                
                hr {
                    height: 0.25em;
                    padding: 0;
                    margin: 24px 0;
                    background-color: $blockquoteBorder;
                    border: 0;
                }
                
                code {
                    font-family: SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace;
                    background-color: $codeBgColor;
                    color: $codeColor;
                    padding: 0.2em 0.4em;
                    border-radius: 6px;
                    font-size: 85%;
                }
                
                pre {
                    background-color: $codeBgColor;
                    padding: 16px;
                    overflow: auto;
                    font-size: 85%;
                    line-height: 1.45;
                    border-radius: 8px;
                    margin-bottom: 16px;
                }
                
                pre code {
                    background-color: transparent;
                    color: inherit;
                    padding: 0;
                    border-radius: 0;
                    font-size: 100%;
                    word-break: normal;
                    white-space: pre;
                }
                
                blockquote {
                    margin: 0 0 16px 0;
                    padding: 0 1em;
                    color: ${if (isDarkMode) "#8E8E93" else "#6A737D"};
                    border-left: 0.25em solid $accentColor;
                    background-color: $blockquoteBg;
                    padding-top: 8px;
                    padding-bottom: 8px;
                    border-radius: 0 4px 4px 0;
                }
                
                table {
                    border-spacing: 0;
                    border-collapse: collapse;
                    width: 100%;
                    margin-top: 0;
                    margin-bottom: 16px;
                    display: block;
                    overflow: auto;
                }
                
                table th, table td {
                    padding: 6px 13px;
                    border: 1px solid $blockquoteBorder;
                }
                
                table th {
                    font-weight: 600;
                    background-color: $blockquoteBg;
                }
                
                table tr {
                    background-color: $backgroundColor;
                }
                
                table tr:nth-child(even) {
                    background-color: $blockquoteBg;
                }
                
                img {
                    max-width: 100%;
                    box-sizing: content-box;
                    background-color: $backgroundColor;
                }
                
                /* Adjust KaTeX displays */
                .katex-display {
                    overflow-x: auto;
                    overflow-y: hidden;
                    padding: 8px 0;
                }
            </style>
            
            <!-- Marked.js (Markdown Parser) -->
            <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
            
            <!-- KaTeX JS Core -->
            <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js" crossorigin="anonymous"></script>
            
            <!-- KaTeX Auto-Render Extension -->
            <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/contrib/auto-render.min.js" crossorigin="anonymous"></script>
        </head>
        <body>
            <div id="content">加载中...</div>

            <script>
                document.addEventListener("DOMContentLoaded", function() {
                    // Initialize marked configuration
                    marked.setOptions({
                        breaks: true,
                        gfm: true
                    });
                    
                    renderMarkdown();
                });

                function renderMarkdown() {
                    try {
                        if (typeof AndroidInterface !== 'undefined') {
                            const markdownText = AndroidInterface.getMarkdown();
                            
                            // Render Markdown to HTML
                            document.getElementById('content').innerHTML = marked.parse(markdownText);
                            
                            // Render Math equations via KaTeX
                            renderMathInElement(document.getElementById('content'), {
                                delimiters: [
                                    {left: "$$", right: "$$", display: true},
                                    {left: "$", right: "$", display: false}
                                ],
                                throwOnError: false
                            });
                        } else {
                            document.getElementById('content').innerHTML = "<p style='color:red;'>数据接口不可用，请重试</p>";
                        }
                    } catch (e) {
                        document.getElementById('content').innerHTML = "<p style='color:red;'>渲染出错: " + e.message + "</p>";
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
