package me.drew.flai.ui.visual

import com.intellij.ui.components.JBScrollPane
import me.drew.flai.domain.model.*
import me.drew.flai.infrastructure.tool.IdeToolRegistry
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

class NodePropertyPanel(private val toolRegistry: IdeToolRegistry) : JPanel(BorderLayout()) {

    var isEditable: Boolean = true
        set(value) {
            field = value
            refreshEnabled()
        }

    private var currentNode: VisualNode? = null
    private var currentModel: VisualPipelineModel? = null
    private var canvas: PipelineCanvas? = null

    private val innerPanel = JPanel()
    private val scrollPane = JBScrollPane(innerPanel)
    private val editableComponents = mutableListOf<JComponent>()

    init {
        innerPanel.layout = BoxLayout(innerPanel, BoxLayout.Y_AXIS)
        innerPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        add(scrollPane, BorderLayout.CENTER)
        showEmpty()
    }

    fun showGate(node: VisualNode?, model: VisualPipelineModel, canvasRef: PipelineCanvas) {
        currentNode = node
        currentModel = model
        canvas = canvasRef
        editableComponents.clear()
        innerPanel.removeAll()

        if (node == null) {
            showEmpty()
            return
        }

        addRow("ID", buildTextField(node.gateId) { newId ->
            val m = currentModel ?: return@buildTextField
            val n = currentNode ?: return@buildTextField
            if (m.renameGateId(n.nodeSeq, newId)) {
                canvasRef.repaint()
            }
        })

        addRow("Label", buildTextField(node.gate.label) { newLabel ->
            updateGate(node.nodeSeq, rebuildWithLabel(node.gate, newLabel))
        })

        addSeparator()

        when (val gate = node.gate) {
            is InputGate -> buildInputGateFields(node.nodeSeq, gate)
            is OutputGate -> buildMappingSection("Output Mapping", gate.outputMapping) { map ->
                updateGate(node.nodeSeq, gate.copy(outputMapping = map))
            }
            is LlmGate -> buildLlmGateFields(node.nodeSeq, gate)
            is LogicGate -> buildLogicGateFields(node.nodeSeq, gate)
            is ToolGate -> buildToolGateFields(node.nodeSeq, gate)
            is ReadFileGate -> {
                addRow("Path", buildTextField(gate.path) { v -> updateGate(node.nodeSeq, gate.copy(path = v)) })
                addRow("Output Key", buildTextField(gate.outputKey) { v -> updateGate(node.nodeSeq, gate.copy(outputKey = v)) })
            }
            is WriteFileGate -> {
                addRow("Path", buildTextField(gate.path) { v -> updateGate(node.nodeSeq, gate.copy(path = v)) })
                addRow("Content Key", buildTextField(gate.contentKey) { v -> updateGate(node.nodeSeq, gate.copy(contentKey = v)) })
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
                        updateGate(node.nodeSeq, gate.copy(mode = mode))
                    }
                }
                editableComponents.add(modeCombo)
                addRow("Mode", modeCombo)
            }
        }

        refreshEnabled()
        innerPanel.revalidate()
        innerPanel.repaint()
    }

    private fun buildInputGateFields(nodeSeq: Int, gate: InputGate) {
        val label = JLabel("Schema Fields")
        label.font = label.font.deriveFont(Font.BOLD)
        innerPanel.add(label)
        innerPanel.add(Box.createVerticalStrut(4))

        val colNames = arrayOf("Name", "Type", "Required", "Default")
        val tableModel = object : DefaultTableModel(colNames, 0) {
            override fun getColumnClass(col: Int) = if (col == 2) Boolean::class.java else String::class.java
            override fun isCellEditable(row: Int, col: Int) = isEditable
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
            updateGate(nodeSeq, gate.copy(inputSchema = fields))
        }

        val table = JTable(tableModel).apply {
            val typeCol = columnModel.getColumn(1)
            typeCol.cellEditor = DefaultCellEditor(JComboBox(FieldType.entries.map { it.name }.toTypedArray()))
            preferredScrollableViewportSize = Dimension(250, 80)
        }
        editableComponents.add(table)
        val tableScroll = JScrollPane(table).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 120)
        }
        innerPanel.add(tableScroll)

        val addBtn = JButton("Add Field").apply {
            alignmentX = LEFT_ALIGNMENT
            addActionListener { tableModel.addRow(arrayOf("field", "STRING", true, "")) }
        }
        val removeBtn = JButton("Remove Selected").apply {
            alignmentX = LEFT_ALIGNMENT
            addActionListener { if (table.selectedRow >= 0) tableModel.removeRow(table.selectedRow) }
        }
        editableComponents.add(addBtn)
        editableComponents.add(removeBtn)
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            add(addBtn); add(removeBtn)
        }
        innerPanel.add(btnRow)
        innerPanel.add(Box.createVerticalStrut(4))
    }

    private fun buildLlmGateFields(nodeSeq: Int, gate: LlmGate) {
        val promptLabel = JLabel("Prompt Template")
        promptLabel.font = promptLabel.font.deriveFont(Font.BOLD)
        innerPanel.add(promptLabel)

        val promptArea = JTextArea(gate.promptTemplate, 6, 20).apply {
            lineWrap = true; wrapStyleWord = true
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = fire()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = fire()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = fire()
                fun fire() { updateGate(nodeSeq, gate.copy(promptTemplate = text)) }
            })
        }
        editableComponents.add(promptArea)
        val promptScroll = JScrollPane(promptArea).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 140)
            preferredSize = Dimension(250, 140)
        }
        innerPanel.add(promptScroll)
        innerPanel.add(Box.createVerticalStrut(4))

        addSection("Endpoint URL", buildTextField(gate.endpointConfig.url) { v ->
            updateGate(nodeSeq, gate.copy(endpointConfig = gate.endpointConfig.copy(url = v)))
        })
        addSection("Credential ID", buildTextField(gate.endpointConfig.credentialId) { v ->
            updateGate(nodeSeq, gate.copy(endpointConfig = gate.endpointConfig.copy(credentialId = v)))
        })
        addSection("Model", buildTextField(gate.endpointConfig.model) { v ->
            updateGate(nodeSeq, gate.copy(endpointConfig = gate.endpointConfig.copy(model = v)))
        })

        addSeparator()
        buildSkillsSection(nodeSeq, gate)
        addSeparator()
        buildMappingSection("Input Mapping", gate.inputMapping) { map ->
            updateGate(nodeSeq, gate.copy(inputMapping = map))
        }
        buildMappingSection("Output Mapping", gate.outputMapping) { map ->
            updateGate(nodeSeq, gate.copy(outputMapping = map))
        }
    }

    private fun buildSkillsSection(nodeSeq: Int, gate: LlmGate) {
        val skillLabel = JLabel("Skills (file paths)")
        skillLabel.font = skillLabel.font.deriveFont(Font.BOLD)
        innerPanel.add(skillLabel)

        val listModel = DefaultListModel<String>()
        gate.skills.forEach { listModel.addElement(it) }
        val list = JList(listModel).apply { visibleRowCount = 3 }
        editableComponents.add(list)

        fun syncSkills() {
            val skills = (0 until listModel.size).map { listModel.getElementAt(it) }
            updateGate(nodeSeq, gate.copy(skills = skills))
        }

        val addBtn = JButton("Add").apply {
            addActionListener {
                val input = JOptionPane.showInputDialog(this@NodePropertyPanel, "Skill file path:")
                if (!input.isNullOrEmpty()) { listModel.addElement(input); syncSkills() }
            }
        }
        val removeBtn = JButton("Remove").apply {
            addActionListener {
                if (list.selectedIndex >= 0) { listModel.remove(list.selectedIndex); syncSkills() }
            }
        }
        editableComponents.add(addBtn); editableComponents.add(removeBtn)
        innerPanel.add(JScrollPane(list).apply { maximumSize = Dimension(Int.MAX_VALUE, 70) })
        innerPanel.add(JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply { add(addBtn); add(removeBtn) })
        innerPanel.add(Box.createVerticalStrut(4))
    }

    private fun buildLogicGateFields(nodeSeq: Int, gate: LogicGate) {
        addRow("Default Port", buildTextField(gate.defaultPort ?: "default") { v ->
            updateGate(nodeSeq, gate.copy(defaultPort = v))
        })
        addSeparator()

        val branchesLabel = JLabel("Branches")
        branchesLabel.font = branchesLabel.font.deriveFont(Font.BOLD)
        innerPanel.add(branchesLabel)
        innerPanel.add(Box.createVerticalStrut(4))

        val branchesHolder = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        innerPanel.add(branchesHolder)

        fun rebuildBranches(branches: List<Branch>) {
            branchesHolder.removeAll()
            branches.forEachIndexed { i, branch ->
                val branchPanel = JPanel(GridBagLayout()).apply {
                    border = BorderFactory.createTitledBorder("Branch ${i + 1}")
                }
                val gbc = GridBagConstraints().apply {
                    insets = Insets(2, 2, 2, 2); fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
                }

                gbc.gridy = 0
                branchPanel.add(JLabel("Port"), gbc.apply { gridx = 0; weightx = 0.3 })
                val portField = JTextField(branch.port, 8)
                editableComponents.add(portField)
                branchPanel.add(portField, gbc.apply { gridx = 1; weightx = 0.7 })

                val condTypes = arrayOf("always", "comparison", "switch")
                val condTypeCombo = JComboBox(condTypes).apply {
                    selectedItem = when (branch.condition) {
                        is BranchCondition.Always -> "always"
                        is BranchCondition.Comparison -> "comparison"
                        is BranchCondition.SwitchCase -> "switch"
                    }
                }
                editableComponents.add(condTypeCombo)
                gbc.gridy = 1
                branchPanel.add(JLabel("Condition"), gbc.apply { gridx = 0; weightx = 0.3 })
                branchPanel.add(condTypeCombo, gbc.apply { gridx = 1; weightx = 0.7 })

                val condDetailPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
                branchPanel.add(condDetailPanel, GridBagConstraints().apply {
                    gridy = 2; gridx = 0; gridwidth = 2
                    fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
                    insets = Insets(2, 2, 2, 2)
                })

                fun buildCondDetail(cond: BranchCondition): Unit {
                    condDetailPanel.removeAll()
                    when (cond) {
                        is BranchCondition.Always -> {}
                        is BranchCondition.Comparison -> {
                            val varField = JTextField(cond.variable, 10)
                            val opCombo = JComboBox(ComparisonOp.entries.map { it.name }.toTypedArray()).apply {
                                selectedItem = cond.op.name
                            }
                            val valField = JTextField(cond.value, 10)
                            editableComponents.addAll(listOf(varField, opCombo as JComponent, valField))
                            condDetailPanel.add(labeledRow("Variable", varField))
                            condDetailPanel.add(labeledRow("Op", opCombo))
                            condDetailPanel.add(labeledRow("Value", valField))
                        }
                        is BranchCondition.SwitchCase -> {
                            val varField = JTextField(cond.variable, 10)
                            val valuesField = JTextField(cond.values.joinToString(","), 10)
                            editableComponents.addAll(listOf(varField, valuesField))
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
                    updateGate(nodeSeq, gate.copy(branches = updatedBranches))
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
                            updateGate(nodeSeq, gate.copy(branches = updatedBranches))
                        }
                        val node = currentModel?.nodeBySeq(nodeSeq) ?: return@addActionListener
                        showGate(node, currentModel!!, canvas!!)
                    }
                }
                editableComponents.add(deleteBtn)
                branchPanel.add(deleteBtn, GridBagConstraints().apply {
                    gridy = 3; gridx = 0; gridwidth = 2; anchor = GridBagConstraints.WEST
                    insets = Insets(2, 2, 2, 2)
                })

                branchesHolder.add(branchPanel)
                branchesHolder.add(Box.createVerticalStrut(4))
            }

            val addBranchBtn = JButton("Add Branch").apply {
                alignmentX = LEFT_ALIGNMENT
                addActionListener {
                    val newBranch = Branch("branch${gate.branches.size + 1}", BranchCondition.Always)
                    updateGate(nodeSeq, gate.copy(branches = gate.branches + newBranch))
                    val node = currentModel?.nodeBySeq(nodeSeq) ?: return@addActionListener
                    showGate(node, currentModel!!, canvas!!)
                }
            }
            editableComponents.add(addBranchBtn)
            branchesHolder.add(addBranchBtn)
            branchesHolder.revalidate(); branchesHolder.repaint()
        }

        rebuildBranches(gate.branches)
    }

    private fun buildToolGateFields(nodeSeq: Int, gate: ToolGate) {
        val toolNames = toolRegistry.listNames()
        if (toolNames.isNotEmpty()) {
            val combo = JComboBox(toolNames.toTypedArray()).apply {
                selectedItem = gate.toolName.takeIf { it.isNotEmpty() } ?: toolNames.first()
                addActionListener {
                    updateGate(nodeSeq, gate.copy(toolName = selectedItem as? String ?: ""))
                }
            }
            editableComponents.add(combo)
            addRow("Tool", combo)
        } else {
            addRow("Tool Name", buildTextField(gate.toolName) { v ->
                updateGate(nodeSeq, gate.copy(toolName = v))
            })
        }
        addSeparator()
        buildMappingSection("Input Mapping", gate.inputMapping) { map ->
            updateGate(nodeSeq, gate.copy(inputMapping = map))
        }
        buildMappingSection("Output Mapping", gate.outputMapping) { map ->
            updateGate(nodeSeq, gate.copy(outputMapping = map))
        }
    }

    private fun buildMappingSection(title: String, map: Map<String, String>, onUpdate: (Map<String, String>) -> Unit) {
        val label = JLabel(title)
        label.font = label.font.deriveFont(Font.BOLD)
        innerPanel.add(label)

        val colNames = arrayOf("From", "To")
        val tableModel = object : DefaultTableModel(colNames, 0) {
            override fun isCellEditable(row: Int, col: Int) = isEditable
        }
        for ((k, v) in map) { tableModel.addRow(arrayOf(k, v)) }

        fun syncMap() {
            val updated = (0 until tableModel.rowCount).mapNotNull { r ->
                val k = tableModel.getValueAt(r, 0) as? String ?: return@mapNotNull null
                val v = tableModel.getValueAt(r, 1) as? String ?: return@mapNotNull null
                if (k.isEmpty()) null else k to v
            }.toMap()
            onUpdate(updated)
        }

        tableModel.addTableModelListener { syncMap() }
        val table = JTable(tableModel).apply {
            preferredScrollableViewportSize = Dimension(250, 60)
        }
        editableComponents.add(table)

        val addBtn = JButton("+").apply { addActionListener { tableModel.addRow(arrayOf("", "")) } }
        val removeBtn = JButton("-").apply { addActionListener { if (table.selectedRow >= 0) tableModel.removeRow(table.selectedRow) } }
        editableComponents.addAll(listOf(addBtn, removeBtn))

        innerPanel.add(JScrollPane(table).apply { maximumSize = Dimension(Int.MAX_VALUE, 80) })
        innerPanel.add(JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply { add(addBtn); add(removeBtn) })
        innerPanel.add(Box.createVerticalStrut(4))
    }

    private fun showEmpty() {
        innerPanel.removeAll()
        innerPanel.add(JLabel("Select a gate to edit properties").apply {
            alignmentX = LEFT_ALIGNMENT
        })
        innerPanel.revalidate()
        innerPanel.repaint()
    }

    private fun addRow(labelText: String, field: JComponent) {
        val row = labeledRow(labelText, field)
        innerPanel.add(row)
    }

    private fun addSection(labelText: String, field: JComponent) = addRow(labelText, field)

    private fun labeledRow(labelText: String, field: JComponent): JPanel {
        return JPanel(BorderLayout(4, 0)).apply {
            add(JLabel("$labelText:").apply { preferredSize = Dimension(110, 24) }, BorderLayout.WEST)
            add(field, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, 30)
        }
    }

    private fun addSeparator() {
        innerPanel.add(Box.createVerticalStrut(6))
        innerPanel.add(JSeparator())
        innerPanel.add(Box.createVerticalStrut(6))
    }

    private fun buildTextField(value: String, onChange: (String) -> Unit): JTextField {
        val field = JTextField(value)
        editableComponents.add(field)
        field.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = onChange(field.text)
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = onChange(field.text)
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = onChange(field.text)
        })
        return field
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

    private fun updateGate(nodeSeq: Int, gate: Gate) {
        currentModel?.updateGate(nodeSeq, gate)
        canvas?.repaint()
    }

    private fun refreshEnabled() {
        editableComponents.forEach { it.isEnabled = isEditable }
    }
}
