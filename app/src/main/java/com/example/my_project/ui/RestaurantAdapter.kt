package com.example.my_project.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.my_project.data.RecommendedRestaurant
import com.example.my_project.databinding.ItemRestaurantBinding

class RestaurantAdapter(
    private var items: List<RecommendedRestaurant>,
    private val onItemClick: (RecommendedRestaurant) -> Unit
) : RecyclerView.Adapter<RestaurantAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemRestaurantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecommendedRestaurant) {
            binding.tvName.text = item.name
            binding.tvCategory.text = item.category.split(">").lastOrNull()?.trim() ?: item.category
            binding.tvDistance.text = "${item.distance}m"
            binding.tvAddress.text = item.address
            binding.tvReason.text = item.reason

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRestaurantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<RecommendedRestaurant>) {
        items = newItems
        notifyDataSetChanged()
    }
}
