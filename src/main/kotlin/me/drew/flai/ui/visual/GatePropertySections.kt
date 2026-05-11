package me.drew.flai.ui.visual

import com.intellij.icons.AllIcons
import me.drew.flai.domain.model.*
import me.drew.flai.infrastructure.tool.IdeToolRegistry
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

data class SectionResult(
    val cards: List<JPanel>,
    val editableComponents: List<JComponent>,
    val firstFocusTarget: JComponent? = null,
)

class GatePropertySections(
    private val toolRegistry: IdeToolRegistry,
    private val onGateUpdated: (nodeSeq: Int, gate: Gate) -> Unit,
    private val onRepaint: () -> Unit,
    private val onRefreshPanel: () -> Unit,
) {

    fun buildBasicInfoFields(nodeSeq: Int, node: VisualNode, model: VisualPipelineModel): SectionResult {
        val editableList = mutableListOf<JComponent>()
        val basicCard = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        basicCard.add(labeledRow("ID", buildTextField(node.gateId, editableList) { newId ->
            model.renameGateId(nodeSeq, newId)
            onRepaint()
        }))
        basicCard.add(labeledRow("Label", buildTextField(node.gate.label, editableList) { newLabel ->
            onGateUpdated(nodeSeq, rebuildWithLabel(node.gate, newLabel))
        }))
        return SectionResult(
            cards = listOf(cardPanel("Basic Info", basicCard)),
            editableComponents = editableList,
        )
    }

    fun buildInputGateFields(nodeSeq: Int, gate: InputGate): SectionResult {
        val editableList = mutableListOf<JComponent>()
        val colNames = arrayOf("Name", "Type", "Required", "Default")
        val tableModel = object : DefaultTableModel(colNames, 0) {
            override fun getColumnClass(col: Int) = if (col == 2) Boolean::class.java else String::class.java
            override fun isCellEditable(row: Int, col: Int) = true
        }
        for (field in gate.inputSchema) {
            tableModel.addRow(arrayOf(field.name, field.type.name, field.required, field.default ?: ""))
        }
        tableModel.addTableModelListener {
            val fields = (0 until tableModel.rowCount).mapNotNull { r ->
                val name = tableModel.getValueAt(r, 0) as? String ?: return@mapNotNull null
                if (name.isEmpty()) return@mapNotNull null
                val typeName = tableModel.getValueAt(r, 1) as? String ?: "STRING"
                val type = runCatching { FieldType.valueOf(typeName.uppercase()) }.getOrDefault(FieldType.STRING)
                val required = tableModel.getValueAt(r, 2) as? Boolean ?: true
                val default = (tableModel.getValueAt(r, 3) as? String)?.takeIf { it.isNotEmpty() }
                InputField(name, type, required, default)
            }
            onGateUpdated(nodeSeq, gate.copy(inputSchema = fields))
        }

        val table = JTable(tableModel).apply {
            val typeCol = columnModel.getColumn(1)
            typeCol.cellEditor = DefaultCellEditor(JComboBox(FieldType.entries.map { it.name }.toTypedArray()))
            preferredScrollableViewportSize = Dimension(250, 80)
        }
        editableList.add(table)
        val tableScroll = JScrollPane(table).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 120)
        }

        val addBtn = JButton("Add Field").apply {
            alignmentX = JComponent.LEFT_ALIGNMENT
            addActionListener { tableModel.addRow(arrayOf("field", "STRING", true, "")) }
        }
        val removeBtn = JButton("Remove Selected").apply {
            alignmentX = JComponent.LEFT_ALIGNMENT
            addActionListener { if (table.selectedRow >= 0) tableModel.removeRow(table.selectedRow) }
        }
        editableList.add(addBtn)
        editableList.add(removeBtn)
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            add(addBtn); add(removeBtn)
        }

        val schemaContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(tableScroll)
            add(btnRow)
        }
        return SectionResult(
            cards = listOf(cardPanel("Schema", schemaContent)),
            editableComponents = editableList,
        )
    }

    fun buildLlmGateFields(nodeSeq: Int, gate: LlmGate): SectionResult {
        val editableList = mutableListOf<JComponent>()
        var firstFocus: JComponent? = null

        val promptArea = JTextArea(gate.promptTemplate, 6, 20).apply {
            lineWrap = true; wrapStyleWord = true
            val origBorder = border
            addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusGained(e: java.awt.event.FocusEvent) {
                    border = BorderFactory.createLineBorder(Color(55, 120, 255), 2)
                }
                override fun focusLost(e: java.awt.event.FocusEvent) {
                    border = origBorder
                }
            })
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = fire()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = fire()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = fire()
                fun fire() { onGateUpdated(nodeSeq, gate.copy(promptTemplate = text)) }
            })
        }
        firstFocus = promptArea
        editableList.add(promptArea)
        val promptScroll = JScrollPane(promptArea).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 140)
            preferredSize = Dimension(250, 140)
        }
        val promptContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(promptScroll)
        }
        val promptCard = cardPanel("Prompt Template", promptContent)

        val endpointContent = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        endpointContent.add(labeledRow("Endpoint URL", buildTextField(gate.endpointConfig.url, editableList) { v ->
            onGateUpdated(nodeSeq, gate.copy(endpointConfig = gate.endpointConfig.copy(url = v)))
        }))
        endpointContent.add(labeledRow("Credential ID", buildTextField(gate.endpointConfig.credentialId, editableList) { v ->
            onGateUpdated(nodeSeq, gate.copy(endpointConfig = gate.endpointConfig.copy(credentialId = v)))
        }))
        endpointContent.add(labeledRow("Model", buildTextField(gate.endpointConfig.model, editableList) { v ->
            onGateUpdated(nodeSeq, gate.copy(endpointConfig = gate.endpointConfig.copy(model = v)))
        }))
        val endpointCard = cardPanel("Endpoint Config", endpointContent)

        val skillsCard = buildSkillsCard(nodeSeq, gate, editableList)

        val inputMappingCards = buildMappingSection("Input Mapping", gate.inputMapping, editableList) { map ->
            onGateUpdated(nodeSeq, gate.copy(inputMapping = map))
        }
        val outputMappingCards = buildMappingSection("Output Mapping", gate.outputMapping, editableList) { map ->
            onGateUpdated(nodeSeq, gate.copy(outputMapping = map))
        }

        return SectionResult(
            cards = listOf(promptCard, endpointCard, skillsCard) + inputMappingCards + outputMappingCards,
            editableComponents = editableList,
            firstFocusTarget = firstFocus,
        )
    }

    private fun buildSkillsCard(nodeSeq: Int, gate: LlmGate, editableList: MutableList<JComponent>): JPanel {
        val listModel = DefaultListModel<String>()
        gate.skills.forEach { listModel.addElement(it) }
        val list = JList(listModel).apply { visibleRowCount = 3 }
        editableList.add(list)

        fun syncSkills() {
            val skills = (0 until listModel.size).map { listModel.getElementAt(it) }
            onGateUpdated(nodeSeq, gate.copy(skills = skills))
        }

        val addBtn = JButton("Add").apply {
            addActionListener {
                val input = JOptionPane.showInputDialog(null, "Skill file path:")
                if (!input.isNullOrEmpty()) { listModel.addElement(input); syncSkills() }
            }
        }
        val removeBtn = JButton("Remove").apply {
            addActionListener {
                if (list.selectedIndex >= 0) { listModel.remove(list.selectedIndex); syncSkills() }
            }
        }
        editableList.add(addBtn); editableList.add(removeBtn)
        val skillsContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JScrollPane(list).apply { maximumSize = Dimension(Int.MAX_VALUE, 70) })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply { add(addBtn); add(removeBtn) })
        }
        return cardPanel("Skills", skillsContent)
    }

    fun buildLogicGateFields(nodeSeq: Int, gate: LogicGate, model: VisualPipelineModel): SectionResult {
        val editableList = mutableListOf<JComponent>()
        val hasDefault = gate.defaultPort != null
        val defaultPortField = JTextField(gate.defaultPort ?: "default")
        defaultPortField.isEnabled = hasDefault
        defaultPortField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = save()
            override fun removeUpdate(e: DocumentEvent) = save()
            override fun changedUpdate(e: DocumentEvent) = save()
            fun save() {
                val current = model.nodeBySeq(nodeSeq)?.gate as? LogicGate ?: return
                onGateUpdated(nodeSeq, current.copy(defaultPort = defaultPortField.text.ifEmpty { null }))
            }
        })
        val defaultPortCheck = JCheckBox().apply {
            isSelected = hasDefault
            addActionListener {
                val enabled = isSelected
                defaultPortField.isEnabled = enabled
                val current = model.nodeBySeq(nodeSeq)?.gate as? LogicGate ?: return@addActionListener
                if (enabled) {
                    val name = defaultPortField.text.ifEmpty { "default" }
                    defaultPortField.text = name
                    onGateUpdated(nodeSeq, current.copy(defaultPort = name))
                } else {
                    onGateUpdated(nodeSeq, current.copy(defaultPort = null))
                }
                onRepaint()
            }
        }
        editableList.add(defaultPortCheck)

        val defaultPortRow = JPanel(BorderLayout(4, 0)).apply {
            add(JLabel("Default Port:").apply {
                preferredSize = Dimension(110, 32)
                font = this.font.deriveFont(Font.PLAIN, 12f)
            }, BorderLayout.WEST)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(defaultPortCheck)
                add(Box.createHorizontalStrut(4))
                add(defaultPortField)
            }, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, 32)
        }
        val settingsContent = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        settingsContent.add(defaultPortRow)
        val settingsCard = cardPanel("Logic Settings", settingsContent)

        val branchesHolder = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val branchesCard = cardPanel("Branches", branchesHolder)

        fun rebuildBranches(branches: List<Branch>) {
            branchesHolder.removeAll()
            branches.forEachIndexed { i, branch ->
                val branchColor = FlaiEditorTheme.branchColor(i)
                val branchPanel = JPanel(GridBagLayout()).apply {
                    val titledBorder = BorderFactory.createTitledBorder("Branch ${i + 1}") as javax.swing.border.TitledBorder
                    titledBorder.titleColor = branchColor
                    border = titledBorder
                }
                val gbc = GridBagConstraints().apply {
                    insets = Insets(2, 2, 2, 2); fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
                }

                gbc.gridy = 0
                branchPanel.add(JLabel("Port"), gbc.apply { gridx = 0; weightx = 0.3 })
                val portField = JTextField(branch.port, 8)
                editableList.add(portField)
                branchPanel.add(portField, gbc.apply { gridx = 1; weightx = 0.7 })

                val condTypes = arrayOf("always", "comparison", "switch")
                val condTypeCombo = JComboBox(condTypes).apply {
                    selectedItem = when (branch.condition) {
                        is BranchCondition.Always -> "always"
                        is BranchCondition.Comparison -> "comparison"
                        is BranchCondition.SwitchCase -> "switch"
                    }
                }
                editableList.add(condTypeCombo)
                gbc.gridy = 1
                branchPanel.add(JLabel("Condition"), gbc.apply { gridx = 0; weightx = 0.3 })
                branchPanel.add(condTypeCombo, gbc.apply { gridx = 1; weightx = 0.7 })

                val condDetailPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
                branchPanel.add(condDetailPanel, GridBagConstraints().apply {
                    gridy = 2; gridx = 0; gridwidth = 2
                    fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
                    insets = Insets(2, 2, 2, 2)
                })

                fun buildCondDetail(cond: BranchCondition) {
                    condDetailPanel.removeAll()
                    when (cond) {
                        is BranchCondition.Always -> {}
                        is BranchCondition.Comparison -> {
                            val varField = JTextField(cond.variable, 10)
                            val opCombo = JComboBox(ComparisonOp.entries.map { it.name }.toTypedArray()).apply {
                                selectedItem = cond.op.name
                            }
                            val valField = JTextField(cond.value, 10)
                            editableList.addAll(listOf(varField, opCombo as JComponent, valField))
                            condDetailPanel.add(labeledRow("Variable", varField))
                            condDetailPanel.add(labeledRow("Op", opCombo))
                            condDetailPanel.add(labeledRow("Value", valField))
                        }
                        is BranchCondition.SwitchCase -> {
                            val varField = JTextField(cond.variable, 10)
                            val valuesField = JTextField(cond.values.joinToString(","), 10)
                            editableList.addAll(listOf(varField, valuesField))
                            condDetailPanel.add(labeledRow("Variable", varField))
                            condDetailPanel.add(labeledRow("Values (csv)", valuesField))
                        }
                    }
                    condDetailPanel.revalidate(); condDetailPanel.repaint()
                }
                buildCondDetail(branch.condition)

                fun saveBranch() {
                    val port = portField.text
                    val cond: BranchCondition = when (condTypeCombo.selectedItem as String) {
                        "always" -> BranchCondition.Always
                        "switch" -> {
                            val rows = condDetailPanel.components
                            val varF = (rows.getOrNull(0) as? JPanel)?.components?.lastOrNull() as? JTextField
                            val valF = (rows.getOrNull(1) as? JPanel)?.components?.lastOrNull() as? JTextField
                            BranchCondition.SwitchCase(varF?.text ?: "", valF?.text?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList())
                        }
                        else -> {
                            val rows = condDetailPanel.components
                            val varF = (rows.getOrNull(0) as? JPanel)?.components?.lastOrNull() as? JTextField
                            val opC = (rows.getOrNull(1) as? JPanel)?.components?.lastOrNull() as? JComboBox<*>
                            val valF = (rows.getOrNull(2) as? JPanel)?.components?.lastOrNull() as? JTextField
                            val op = runCatching { ComparisonOp.valueOf(opC?.selectedItem as? String ?: "EQ") }.getOrDefault(ComparisonOp.EQ)
                            BranchCondition.Comparison(varF?.text ?: "", op, valF?.text ?: "")
                        }
                    }
                    val updatedBranches = gate.branches.toMutableList()
                    if (i < updatedBranches.size) updatedBranches[i] = Branch(port, cond)
                    onGateUpdated(nodeSeq, gate.copy(branches = updatedBranches))
                }

                condTypeCombo.addActionListener {
                    val newCond: BranchCondition = when (condTypeCombo.selectedItem as String) {
                        "always" -> BranchCondition.Always
                        "switch" -> BranchCondition.SwitchCase("", emptyList())
                        else -> BranchCondition.Comparison("", ComparisonOp.EQ, "")
                    }
                    buildCondDetail(newCond)
                    saveBranch()
                }
                portField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                    override fun insertUpdate(e: javax.swing.event.DocumentEvent) = saveBranch()
                    override fun removeUpdate(e: javax.swing.event.DocumentEvent) = saveBranch()
                    override fun changedUpdate(e: javax.swing.event.DocumentEvent) = saveBranch()
                })

                val deleteBtn = JButton("Delete Branch").apply {
                    addActionListener {
                        val updatedBranches = gate.branches.toMutableList()
                        if (i < updatedBranches.size) {
                            updatedBranches.removeAt(i)
                            onGateUpdated(nodeSeq, gate.copy(branches = updatedBranches))
                        }
                        onRefreshPanel()
                    }
                }
                editableList.add(deleteBtn)
                branchPanel.add(deleteBtn, GridBagConstraints().apply {
                    gridy = 3; gridx = 0; gridwidth = 2; anchor = GridBagConstraints.WEST
                    insets = Insets(2, 2, 2, 2)
                })

                branchesHolder.add(branchPanel)
                branchesHolder.add(Box.createVerticalStrut(4))
            }

            val addBranchBtn = JButton("Add Branch").apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
                addActionListener {
                    val newBranch = Branch("branch${gate.branches.size + 1}", BranchCondition.Always)
                    onGateUpdated(nodeSeq, gate.copy(branches = gate.branches + newBranch))
                    onRefreshPanel()
                }
            }
            editableList.add(addBranchBtn)
            branchesHolder.add(addBranchBtn)
            branchesHolder.revalidate(); branchesHolder.repaint()
        }

        rebuildBranches(gate.branches)

        return SectionResult(
            cards = listOf(settingsCard, branchesCard),
            editableComponents = editableList,
        )
    }

    fun buildToolGateFields(nodeSeq: Int, gate: ToolGate): SectionResult {
        val editableList = mutableListOf<JComponent>()
        val toolContent = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val toolNames = toolRegistry.listNames()
        if (toolNames.isNotEmpty()) {
            val combo = JComboBox(toolNames.toTypedArray()).apply {
                selectedItem = gate.toolName.takeIf { it.isNotEmpty() } ?: toolNames.first()
                addActionListener {
                    onGateUpdated(nodeSeq, gate.copy(toolName = selectedItem as? String ?: ""))
                }
            }
            editableList.add(combo)
            toolContent.add(labeledRow("Tool", combo))
        } else {
            toolContent.add(labeledRow("Tool Name", buildTextField(gate.toolName, editableList) { v ->
                onGateUpdated(nodeSeq, gate.copy(toolName = v))
            }))
        }
        val toolCard = cardPanel("Tool Settings", toolContent)

        val inputMappingCards = buildMappingSection("Input Mapping", gate.inputMapping, editableList) { map ->
            onGateUpdated(nodeSeq, gate.copy(inputMapping = map))
        }
        val outputMappingCards = buildMappingSection("Output Mapping", gate.outputMapping, editableList) { map ->
            onGateUpdated(nodeSeq, gate.copy(outputMapping = map))
        }

        return SectionResult(
            cards = listOf(toolCard) + inputMappingCards + outputMappingCards,
            editableComponents = editableList,
        )
    }

    fun buildOutputGateFields(nodeSeq: Int, gate: OutputGate): SectionResult {
        val editableList = mutableListOf<JComponent>()
        val cards = buildMappingSection("Output Mapping", gate.outputMapping, editableList) { map ->
            onGateUpdated(nodeSeq, gate.copy(outputMapping = map))
        }
        return SectionResult(cards = cards, editableComponents = editableList)
    }

    fun buildReadFileGateFields(nodeSeq: Int, gate: ReadFileGate): SectionResult {
        val editableList = mutableListOf<JComponent>()
        val fileCard = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        fileCard.add(labeledRow("Path", buildTextField(gate.path, editableList) { v -> onGateUpdated(nodeSeq, gate.copy(path = v)) }))
        fileCard.add(labeledRow("Output Key", buildTextField(gate.outputKey, editableList) { v -> onGateUpdated(nodeSeq, gate.copy(outputKey = v)) }))
        return SectionResult(
            cards = listOf(cardPanel("File Settings", fileCard)),
            editableComponents = editableList,
        )
    }

    fun buildWriteFileGateFields(nodeSeq: Int, gate: WriteFileGate): SectionResult {
        val editableList = mutableListOf<JComponent>()
        val fileCard = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        fileCard.add(labeledRow("Path", buildTextField(gate.path, editableList) { v -> onGateUpdated(nodeSeq, gate.copy(path = v)) }))
        fileCard.add(labeledRow("Content Key", buildTextField(gate.contentKey, editableList) { v -> onGateUpdated(nodeSeq, gate.copy(contentKey = v)) }))
        val modeCombo = JComboBox(arrayOf("overwrite", "append", "fail-if-exists")).apply {
            selectedItem = when (gate.mode) {
                WriteMode.OVERWRITE -> "overwrite"
                WriteMode.APPEND -> "append"
                WriteMode.FAIL_IF_EXISTS -> "fail-if-exists"
            }
            addActionListener {
                val mode = when (selectedItem) {
                    "append" -> WriteMode.APPEND
                    "fail-if-exists" -> WriteMode.FAIL_IF_EXISTS
                    else -> WriteMode.OVERWRITE
                }
                onGateUpdated(nodeSeq, gate.copy(mode = mode))
            }
        }
        editableList.add(modeCombo)
        fileCard.add(labeledRow("Mode", modeCombo))
        return SectionResult(
            cards = listOf(cardPanel("File Settings", fileCard)),
            editableComponents = editableList,
        )
    }

    fun buildMappingSection(
        title: String,
        map: Map<String, String>,
        editableList: MutableList<JComponent>,
        onUpdate: (Map<String, String>) -> Unit,
    ): List<JPanel> {
        val rows: MutableList<Array<String>> = map.map { (k, v) -> arrayOf(k, v) }.toMutableList()

        val rowsHolder = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        fun syncMap() {
            val updated = rows.mapNotNull { row ->
                val k = row[0]
                val v = row[1]
                if (k.isEmpty()) null else k to v
            }.toMap()
            onUpdate(updated)
        }

        fun rebuildRows() {
            rowsHolder.removeAll()
            if (rows.isEmpty()) {
                val emptyLabel = JLabel("No mappings added").apply {
                    foreground = UIManager.getColor("Label.disabledForeground")
                    alignmentX = JComponent.LEFT_ALIGNMENT
                    border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
                }
                rowsHolder.add(emptyLabel)
            } else {
                rows.forEachIndexed { i, row ->
                    val fromField = JTextField(row[0]).apply {
                        maximumSize = Dimension(Int.MAX_VALUE, 28)
                    }
                    val toField = JTextField(row[1]).apply {
                        maximumSize = Dimension(Int.MAX_VALUE, 28)
                    }
                    val trashBtn = JButton(AllIcons.Actions.GC).apply {
                        toolTipText = "Remove this mapping"
                        preferredSize = Dimension(24, 24)
                        isBorderPainted = false
                        isContentAreaFilled = false
                    }
                    editableList.addAll(listOf(fromField, toField, trashBtn))

                    fromField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent) { rows[i][0] = fromField.text; syncMap() }
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent) { rows[i][0] = fromField.text; syncMap() }
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent) { rows[i][0] = fromField.text; syncMap() }
                    })
                    toField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                        override fun insertUpdate(e: javax.swing.event.DocumentEvent) { rows[i][1] = toField.text; syncMap() }
                        override fun removeUpdate(e: javax.swing.event.DocumentEvent) { rows[i][1] = toField.text; syncMap() }
                        override fun changedUpdate(e: javax.swing.event.DocumentEvent) { rows[i][1] = toField.text; syncMap() }
                    })
                    trashBtn.addActionListener {
                        rows.removeAt(i)
                        syncMap()
                        rebuildRows()
                        rowsHolder.revalidate()
                        rowsHolder.repaint()
                    }

                    val rowPanel = JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.X_AXIS)
                        alignmentX = JComponent.LEFT_ALIGNMENT
                        add(fromField)
                        add(Box.createHorizontalStrut(4))
                        add(toField)
                        add(Box.createHorizontalStrut(2))
                        add(trashBtn)
                        maximumSize = Dimension(Int.MAX_VALUE, 30)
                    }
                    rowsHolder.add(rowPanel)
                    rowsHolder.add(Box.createVerticalStrut(2))
                }
            }
            rowsHolder.revalidate()
            rowsHolder.repaint()
        }

        rebuildRows()

        val addBtn = JButton("+ Add").apply {
            alignmentX = JComponent.LEFT_ALIGNMENT
            addActionListener {
                rows.add(arrayOf("", ""))
                rebuildRows()
                rowsHolder.revalidate()
                rowsHolder.repaint()
                syncMap()
            }
        }
        editableList.add(addBtn)

        val mappingContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(rowsHolder)
            add(addBtn)
        }
        return listOf(cardPanel(title, mappingContent))
    }

    fun buildTextField(value: String, editableList: MutableList<JComponent>, onChange: (String) -> Unit): JTextField {
        val field = JTextField(value)
        editableList.add(field)
        field.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = onChange(field.text)
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = onChange(field.text)
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onChange(field.text)
        })
        val originalBorder = field.border
        field.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent) {
                field.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color(55, 120, 255), 2),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1),
                )
            }
            override fun focusLost(e: java.awt.event.FocusEvent) {
                field.border = originalBorder
            }
        })
        return field
    }

    fun labeledRow(labelText: String, field: JComponent): JPanel {
        return JPanel(BorderLayout(4, 0)).apply {
            add(JLabel("$labelText:").apply {
                preferredSize = Dimension(110, 32)
                font = this.font.deriveFont(Font.PLAIN, 12f)
            }, BorderLayout.WEST)
            add(field, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, 32)
        }
    }

    fun cardPanel(title: String, vararg contents: JComponent): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            val titledBorder = BorderFactory.createTitledBorder(title)
            (titledBorder as javax.swing.border.TitledBorder).titleFont =
                font.deriveFont(Font.BOLD, 13f)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 0, 4, 0),
                titledBorder,
            )
            for (c in contents) add(c)
        }
    }

    private fun rebuildWithLabel(gate: Gate, newLabel: String): Gate = when (gate) {
        is InputGate -> gate.copy(label = newLabel)
        is OutputGate -> gate.copy(label = newLabel)
        is LlmGate -> gate.copy(label = newLabel)
        is LogicGate -> gate.copy(label = newLabel)
        is ToolGate -> gate.copy(label = newLabel)
        is ReadFileGate -> gate.copy(label = newLabel)
        is WriteFileGate -> gate.copy(label = newLabel)
    }
}
