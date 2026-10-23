/*
 * Copyright 2026 r4l.launcher contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 * Distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
package app.murinelauncher.drawer

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import app.murinelauncher.drawer.CategoryRepository.PickerApp
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.R as MaterialR

/** Lets the user choose exactly [CategoryState.PICK_COUNT] categories; Other is always added. */
object CategoryPicker {
    /** Loads the installed apps off the main thread, then shows the picker. */
    @JvmStatic
    @JvmOverloads
    fun show(context: Context, repository: CategoryRepository, onSaved: () -> Unit = {}) {
        UI_HELPER_EXECUTOR.execute {
            val apps = repository.installedApps()
            MAIN_EXECUTOR.execute {
                val activity = context as? Activity
                if (activity == null || !activity.isFinishing && !activity.isDestroyed) {
                    show(context, repository, apps, upgrade = false, onSaved)
                }
            }
        }
    }

    /**
     * With [upgrade], dismissing the picker keeps the preselected categories and the picker
     * isn't offered again; otherwise cancelling changes nothing.
     */
    fun show(
        context: Context, repository: CategoryRepository, apps: List<PickerApp>,
        upgrade: Boolean, onSaved: () -> Unit = {},
    ) {
        val themed = DrawerCategoryTheme.wrap(context)
        val state = repository.state()
        val picked = LinkedHashSet(if (state.confirmed) state.selected else CategoryState.suggest(
            apps.map { state.primaryFor(it.key, it.packageName, it.androidCategory) }, state.selected))
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val title = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
            val primary = MaterialColors.getColor(this, android.R.attr.colorPrimary)
            addView(ImageView(themed).apply {
                setImageResource(R.drawable.ic_category_manage)
                imageTintList = ColorStateList.valueOf(primary)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(padding * 4 / 3, padding * 4 / 3))
            addView(MaterialTextView(themed).apply {
                setTextAppearance(MaterialR.style.TextAppearance_Material3_HeadlineMedium)
                setTypeface(typeface, Typeface.BOLD)
                setText(R.string.drawer_category_choose)
                setPadding(0, padding / 2, 0, 0)
                ViewCompat.setAccessibilityHeading(this, true)
            })
        }
        val message = MaterialTextView(themed).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
            text = context.getString(R.string.drawer_category_choose_message, CategoryState.PICK_COUNT)
            setPadding(0, 0, 0, padding / 2)
        }
        val count = MaterialTextView(themed).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
            setPadding(0, 0, 0, padding / 2)
        }
        // Chips already pad themselves to a 48 dp touch target.
        val group = ChipGroup(themed).apply { chipSpacingVertical = 0 }
        val chips = CategoryState.CATALOG.associateWith { id ->
            Chip(themed).apply {
                setChipDrawable(ChipDrawable.createFromAttributes(themed, null, 0,
                    MaterialR.style.Widget_Material3_Chip_Filter))
                setChipIconResource(CategoryIcons.drawable(id))
                chipIconTint = textColors
                isChipIconVisible = true
                // Checked chips are already tinted; a checkmark would cover the category icon.
                isCheckedIconVisible = false
                isChecked = id in picked
                group.addView(this)
            }
        }
        val content = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(message)
            addView(count)
            addView(group)
        }
        var saved = false
        val dialog = MaterialAlertDialogBuilder(themed)
            .setCustomTitle(title)
            .setView(ScrollView(themed).apply { addView(content) })
            .setPositiveButton(R.string.drawer_category_save) { _, _ ->
                saved = true
                repository.edit { it.select(picked) }
                onSaved()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                if (upgrade && !saved) repository.edit { it.confirmed = true }
            }
            .create()

        fun update() {
            chips.forEach { (id, chip) ->
                // How many apps this category would hold if it were picked alongside the others.
                val selection = picked + id
                val apps = apps.count {
                    state.categoryFor(it.key, it.packageName, it.androidCategory, selection) == id
                }
                chip.text = context.getString(R.string.drawer_category_chip,
                    repository.title(state.categories.first { it.id == id }), apps)
                chip.isEnabled = chip.isChecked || picked.size < CategoryState.PICK_COUNT
            }
            count.text = context.getString(R.string.drawer_category_choose_count,
                picked.size, CategoryState.PICK_COUNT)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled =
                picked.size == CategoryState.PICK_COUNT
        }
        chips.forEach { (id, chip) ->
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) picked.add(id) else picked.remove(id)
                update()
            }
        }
        dialog.setOnShowListener { update() }
        update()
        dialog.show()
    }
}
