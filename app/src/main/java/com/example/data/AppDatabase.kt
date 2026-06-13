package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Document::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "markdown_editor_db"
                )
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seed database on creation
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.documentDao())
                    }
                }
            }

            suspend fun populateDatabase(documentDao: DocumentDao) {
                // Document 1
                val doc1 = Document(
                    title = "Markdown 快捷指南",
                    content = """# 欢迎使用 Markdown 编辑器 👋

这是一个轻量化、美观的外观、支持 **Markdown** 与 **LaTeX** 的文档编辑器。

## 常用 Markdown 语法

### 1. 文本样式
*   **加粗文本** (`**加粗**`)
*   *斜体文本* (`*斜体*`)
*   ~~删除线~~ (`~~删除线~~`)
*   `单行行内代码` (使用英文反引号包裹)

### 2. 列表
#### 无序列表：
*   轻量和高效
*   美观的 Material 3 界面
*   完美的 LaTeX 实时公式渲染

#### 有序列表：
1.  新建一个文档
2.  输入 Markdown 及 LaTeX 语法
3.  点击右上角眼睛，进入阅读视图，或者导出为 PDF、Markdown 及网页！

### 3. 表格表示

| 项目 | 功能说明 | 状态 |
| :--- | :--- | :---: |
| 实时预览 | 编辑和阅读无缝切换 | 已完成 │
| LaTeX 公式 | 完美支持数学与化学符号 | 已完成 │
| 快速导出 | 生成 PDF/HTML/MD 文档 | 已完成 │

### 4. 引用与分割线
> 书山有路勤为径，学海无涯苦作舟。
> ———— 韩愈

---

### 5. 代码高亮

```kotlin
fun main() {
    val message = "Hello, Markdown + LaTeX!"
    println(message)
}
```
""",
                    createdAt = System.currentTimeMillis() - 60000,
                    updatedAt = System.currentTimeMillis() - 60000,
                    isFavorite = true,
                    tags = "指南,教程"
                )

                // Document 2
                val s = "$"
                val d = "$$"
                val doc2 = Document(
                    title = "LaTeX 数学物理公式参考",
                    content = """# LaTeX 数学物理公式排版 📐

本编辑器集成了卓越的 **KaTeX 数学方程库**，为您提供丝滑、准确的高频数学符号渲染。

## 1. 行内公式 (Inline Math)

将公式包裹在单个 `${s}` 符号中。例如：
*   质能方程：${s}E = mc^2${s} 表示能量与质量的关系。
*   欧拉恒等式：${s}e^{i\pi} + 1 = 0${s} 被誉为最完美的方程。
*   二次方程根公式：${s}x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}${s}。

---

## 2. 独立块级公式 (Block Math)

双美元符号 `${d}` 包裹居中独立显示的数学公式。

### 麦克斯韦电磁方程组 (Maxwell's Equations)

${d}
\begin{aligned}
\nabla \cdot \mathbf{E} &= \frac{\rho}{\varepsilon_0} \\
\nabla \cdot \mathbf{B} &= 0 \\
\nabla \times \mathbf{E} &= -\frac{\partial \mathbf{B}}{\partial t} \\
\nabla \times \mathbf{B} &= \mu_0 \mathbf{J} + \mu_0 \varepsilon_0 \frac{\partial \mathbf{E}}{\partial t}
\end{aligned}
${d}

### 傅里叶变换 (Fourier Transform)

${d}f(x) = \int_{-\infty}^{\infty} \hat{f}(\xi)\,e^{2 \pi i \xi x}\,\mathrm{d}\xi${d}

### 薛定谔方程 (Schrödinger Equation)

${d}i\hbar\frac{\partial}{\partial t}\Psi(\mathbf{r},t) = \left[-\frac{\hbar^2}{2m}\nabla^2 + V(\mathbf{r},t)\right]\Psi(\mathbf{r},t)${d}

### 矩阵与积分 (Matrices & Integration)

${d}
\mathbf{BlockMatrix} = 
\begin{pmatrix}
1 & \alpha & \beta \\
\gamma & 2 & \delta \\
\epsilon & \zeta & 3
\end{pmatrix}
\times
\int_{0}^{\pi} \sin(x) \, \mathrm{d}x
${d}

尽情开始写属于你自己的科学方程式吧！
""",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isFavorite = false,
                    tags = "LaTeX,数学,物理"
                )

                documentDao.insertDocument(doc1)
                documentDao.insertDocument(doc2)
            }
        }
    }
}
