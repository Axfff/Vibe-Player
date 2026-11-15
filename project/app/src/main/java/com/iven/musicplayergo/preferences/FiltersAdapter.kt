package com.iven.musicplayergo.preferences

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.databinding.FilterItemBinding
import com.iven.musicplayergo.utils.Theming


class FiltersAdapter: RecyclerView.Adapter<FiltersAdapter.CheckableItemsHolder>() {

    private val mItemsToRemove = mutableListOf<String>()

    private val mAvailableItems = GoPreferences.getPrefsInstance().filters?.sorted()?.toMutableList()

    fun getUpdatedItems() = mAvailableItems?.apply {
        removeAll(mItemsToRemove.toSet())
    }?.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheckableItemsHolder {
        val binding = FilterItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CheckableItemsHolder(binding)
    }

    override fun getItemCount() = mAvailableItems?.size!!

    override fun onBindViewHolder(holder: CheckableItemsHolder, position: Int) {
        holder.bindItems(mAvailableItems?.get(position))
    }

    inner class CheckableItemsHolder(private val binding: FilterItemBinding): RecyclerView.ViewHolder(binding.root) {

        fun bindItems(itemFilter: String?) {

            with(binding) {

                val context = binding.root.context

                fun updateTitleColor(isChecked: Boolean) {
                    val color = if (isChecked) {
                        Theming.resolveColorAttr(context, android.R.attr.textColorPrimary)
                    } else {
                        Theming.resolveWidgetsColorNormal(context)
                    }
                    title.setTextColor(color)
                }

                itemFilter?.let { itemFiltered ->

                    title.text = itemFiltered
                    filter.setOnCheckedChangeListener(null)
                    filter.isChecked = itemFiltered !in mItemsToRemove
                    updateTitleColor(filter.isChecked)
                    filter.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            mItemsToRemove.remove(itemFiltered)
                        } else {
                            mItemsToRemove.add(itemFiltered)
                        }
                        updateTitleColor(isChecked)
                    }

                    root.setOnClickListener {
                        filter.isChecked = !filter.isChecked
                    }
                }
            }
        }
    }
}
