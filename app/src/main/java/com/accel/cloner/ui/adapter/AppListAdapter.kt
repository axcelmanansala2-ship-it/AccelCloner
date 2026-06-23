package com.accel.cloner.ui.adapter

import android.view.*
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.accel.cloner.R
import com.accel.cloner.core.AppInfo

class AppListAdapter(
    private val onClone: (AppInfo) -> Unit,
    private val onRemove: (AppInfo) -> Unit,
    private val onGGAttach: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgIcon)
        val name: TextView  = view.findViewById(R.id.tvAppName)
        val pkg: TextView   = view.findViewById(R.id.tvPackage)
        val btnClone: Button = view.findViewById(R.id.btnClone)
        val btnRemove: Button = view.findViewById(R.id.btnRemove)
        val btnGG: Button   = view.findViewById(R.id.btnGG)
        val cloneBadge: TextView = view.findViewById(R.id.tvCloneBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.pkg.text = app.packageName
        holder.cloneBadge.visibility = if (app.isCloned) View.VISIBLE else View.GONE
        holder.btnClone.setOnClickListener { onClone(app) }
        holder.btnRemove.visibility = if (app.isCloned) View.VISIBLE else View.GONE
        holder.btnRemove.setOnClickListener { onRemove(app) }
        holder.btnGG.setOnClickListener { onGGAttach(app) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(a: AppInfo, b: AppInfo) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppInfo, b: AppInfo) = a == b
        }
    }
}
