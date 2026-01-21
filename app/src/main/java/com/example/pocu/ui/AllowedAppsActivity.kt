package com.example.pocu.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pocu.R
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityAllowedAppsBinding
import com.example.pocu.databinding.ItemAppBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    var isAllowed: Boolean
)

class AllowedAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllowedAppsBinding
    private lateinit var prefs: AppPreferences
    private lateinit var adapter: AppAdapter

    private var allApps = listOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllowedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupFab()
        loadApps()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter { appInfo, isAllowed ->
            appInfo.isAllowed = isAllowed
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
        })
    }

    private fun setupFab() {
        binding.fabSave.setOnClickListener {
            saveAllowedApps()
        }
    }

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvApps.visibility = View.GONE

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                loadInstalledApps()
            }
            allApps = apps
            adapter.submitList(apps)

            binding.progressBar.visibility = View.GONE
            binding.rvApps.visibility = View.VISIBLE
            binding.tvEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val allowedApps = prefs.getAllowedApps()

        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                // Only show apps with launcher intent (user apps)
                pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { app ->
                AppInfo(
                    packageName = app.packageName,
                    appName = app.loadLabel(pm).toString(),
                    icon = app.loadIcon(pm),
                    isAllowed = allowedApps.contains(app.packageName)
                )
            }
            .sortedWith(compareByDescending<AppInfo> { it.isAllowed }.thenBy { it.appName.lowercase() })

        return installedApps
    }

    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            adapter.submitList(allApps)
        } else {
            val filtered = allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
            adapter.submitList(filtered)
        }
    }

    private fun saveAllowedApps() {
        val allowedPackages = allApps
            .filter { it.isAllowed }
            .map { it.packageName }
            .toMutableSet()

        // Always include our own app
        allowedPackages.add(packageName)

        prefs.saveAllowedApps(allowedPackages)
        Toast.makeText(this, getString(R.string.apps_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    inner class AppAdapter(
        private val onCheckedChange: (AppInfo, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        private var apps = listOf<AppInfo>()

        fun submitList(list: List<AppInfo>) {
            apps = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAppBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(apps[position])
        }

        override fun getItemCount() = apps.size

        inner class ViewHolder(private val binding: ItemAppBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(appInfo: AppInfo) {
                binding.ivAppIcon.setImageDrawable(appInfo.icon)
                binding.tvAppName.text = appInfo.appName
                binding.tvPackageName.text = appInfo.packageName

                binding.checkBox.setOnCheckedChangeListener(null)
                binding.checkBox.isChecked = appInfo.isAllowed
                binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
                    onCheckedChange(appInfo, isChecked)
                }

                binding.root.setOnClickListener {
                    binding.checkBox.isChecked = !binding.checkBox.isChecked
                }
            }
        }
    }
}

