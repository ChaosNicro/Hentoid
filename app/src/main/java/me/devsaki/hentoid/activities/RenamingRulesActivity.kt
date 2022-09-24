package me.devsaki.hentoid.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import com.mikepenz.fastadapter.select.SelectExtension
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.RenamingRuleBundle
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.databinding.ActivityRulesBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.fragments.metadata.MetaEditRuleDialogFragment
import me.devsaki.hentoid.fragments.metadata.MetaEditRuleTopPanel
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.viewholders.RuleItem
import me.devsaki.hentoid.viewmodels.RulesEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper


class RenamingRulesActivity : BaseActivity(), MetaEditRuleDialogFragment.Parent {

    // == Communication
    private lateinit var viewModel: RulesEditViewModel

    // == MAIN SCREEN UI
    private var binding: ActivityRulesBinding? = null

    // Rules
    private val itemAdapter = ItemAdapter<RuleItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private lateinit var selectExtension: SelectExtension<RuleItem>

    // == Vars
    private var queryFilter = ""
    private var attributeTypeFilter = AttributeType.UNDEFINED

    private val ruleItemDiffCallback: DiffCallback<RuleItem> =
        object : DiffCallback<RuleItem> {
            override fun areItemsTheSame(oldItem: RuleItem, newItem: RuleItem): Boolean {
                return oldItem.identifier == newItem.identifier
            }

            override fun areContentsTheSame(
                oldItem: RuleItem,
                newItem: RuleItem
            ): Boolean {
                return oldItem.attrType == newItem.attrType
                        && oldItem.source.equals(newItem.source, true)
                        && oldItem.target.equals(newItem.target, true)
            }

            override fun getChangePayload(
                oldItem: RuleItem,
                oldItemPosition: Int,
                newItem: RuleItem,
                newItemPosition: Int
            ): Any? {
                val diffBundleBuilder = RenamingRuleBundle()
                if (oldItem.attrType != newItem.attrType) {
                    diffBundleBuilder.attrType = newItem.attrType.code
                }
                if (oldItem.source != newItem.source) {
                    diffBundleBuilder.source = newItem.source
                }
                if (oldItem.target != newItem.target) {
                    diffBundleBuilder.target = newItem.target
                }
                return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[RulesEditViewModel::class.java]

        bindUI()
        bindInteractions()

        viewModel.getRules().observe(this) { this.onRulesChanged(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun onRulesChanged(rules: List<RenamingRule>) {
        val items = rules.map { r -> RuleItem(r) }
        FastAdapterDiffUtil.set(itemAdapter, items, ruleItemDiffCallback)
    }

    private fun bindUI() {
        binding?.let {
            it.toolbar.setNavigationOnClickListener { onBackPressed() }
            it.toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked)
            it.selectionToolbar.setOnMenuItemClickListener(this::onSelectionToolbarItemClicked)

            it.tagsFab.setOnClickListener {
                MetaEditRuleDialogFragment.invoke(this, true, 0, attributeTypeFilter)
            }
        }
    }

    private fun bindInteractions() {
        binding?.let { it ->
            // Rules list init
            it.list.adapter = fastAdapter
            selectExtension = fastAdapter.requireOrCreateExtension()
            selectExtension.let { se ->
                se.isSelectable = true
                se.multiSelect = true
                se.selectOnLongClick = true
                se.selectWithItemUpdate = true
                se.selectionListener = object : ISelectionListener<RuleItem> {
                    override fun onSelectionChanged(item: RuleItem, selected: Boolean) {
                        onSelectionChanged()
                    }
                }
                val helper = FastAdapterPreClickSelectHelper(se)
                fastAdapter.onPreClickListener =
                    { v: View?, adapter: IAdapter<RuleItem>?, item: RuleItem, position: Int? ->
                        helper.onPreClickListener(v, adapter, item, position)
                    }
                fastAdapter.onPreLongClickListener =
                    { v: View?, adapter: IAdapter<RuleItem>?, item: RuleItem, position: Int? ->
                        helper.onPreClickListener(v, adapter, item, position)
                    }
            }
            fastAdapter.onClickListener =
                { _: View?, _: IAdapter<RuleItem>, i: RuleItem, _: Int ->
                    onItemClick(i)
                }
        }
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedItems: Set<RuleItem> = selectExtension.selectedItems
        val selectedCount = selectedItems.size
        if (0 == selectedCount) {
            binding?.selectionToolbar?.visibility = View.GONE
            selectExtension.selectOnLongClick = true
        } else {
            binding?.selectionToolbar?.visibility = View.VISIBLE
        }
    }

    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
//            R.id.action_search -> confirmEdit()
            R.id.action_sort_filter -> showSortFilterPanel()
            else -> return true
        }
        return true
    }

    private fun onSelectionToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_delete -> deleteSelectedItems()
            else -> return true
        }
        return true
    }

    private fun showSortFilterPanel() {
        val topPanel = MetaEditRuleTopPanel(this)
        topPanel.showAsDropDown(binding!!.toolbar)
    }

    private fun deleteSelectedItems() {
        val selectedItems = selectExtension.selectedItems
        val builder = MaterialAlertDialogBuilder(this)
        val title = resources.getQuantityString(
            R.plurals.ask_delete_multiple,
            selectedItems.size,
            selectedItems.size
        )
        builder.setMessage(title)
            .setPositiveButton(
                R.string.ok
            ) { _, _ ->
                viewModel.removeRules(selectedItems.map { i -> i.rule.id })
            }
            .setNegativeButton(R.string.cancel, null)
            .create().show()
    }


    /**
     * Callback for attribute item click
     *
     * @param item RuleItem that has been clicked on
     */
    private fun onItemClick(item: RuleItem): Boolean {
        if (selectExtension.selectOnLongClick) {
            editRule(item.rule)
            return true
        }
        return false
    }


    private fun editRule(rule: RenamingRule) {
        MetaEditRuleDialogFragment.invoke(this, false, rule.id)
    }

    override fun onCreateRule(type: AttributeType, source: String, target: String) {
        viewModel.createRule(type, source, target)
    }

    override fun onEditRule(id: Long, source: String, target: String) {
        viewModel.editRule(id, source, target)
    }

    override fun onRemoveRule(id: Long) {
        viewModel.removeRules(listOf(id))
    }
}