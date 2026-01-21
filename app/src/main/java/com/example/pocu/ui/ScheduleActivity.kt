package com.example.pocu.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pocu.R
import com.example.pocu.data.AppPreferences
import com.example.pocu.data.Schedule
import com.example.pocu.databinding.ActivityScheduleBinding
import com.example.pocu.databinding.DialogAddScheduleBinding
import com.example.pocu.databinding.ItemScheduleBinding

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private lateinit var prefs: AppPreferences
    private lateinit var adapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadSchedules()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ScheduleAdapter(
            onToggle = { schedule, enabled ->
                prefs.updateSchedule(schedule.copy(enabled = enabled))
            },
            onDelete = { schedule ->
                prefs.removeSchedule(schedule.id)
                loadSchedules()
            }
        )
        binding.rvSchedules.layoutManager = LinearLayoutManager(this)
        binding.rvSchedules.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddScheduleDialog()
        }
    }

    private fun loadSchedules() {
        val schedules = prefs.getSchedules()
        adapter.submitList(schedules)

        binding.tvEmpty.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSchedules.visibility = if (schedules.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showAddScheduleDialog() {
        val dialogBinding = DialogAddScheduleBinding.inflate(layoutInflater)

        var startHour = 8
        var startMinute = 0
        var endHour = 14
        var endMinute = 0

        dialogBinding.btnStartTime.text = String.format("%02d:%02d", startHour, startMinute)
        dialogBinding.btnEndTime.text = String.format("%02d:%02d", endHour, endMinute)

        dialogBinding.btnStartTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                startHour = hour
                startMinute = minute
                dialogBinding.btnStartTime.text = String.format("%02d:%02d", hour, minute)
            }, startHour, startMinute, true).show()
        }

        dialogBinding.btnEndTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                endHour = hour
                endMinute = minute
                dialogBinding.btnEndTime.text = String.format("%02d:%02d", hour, minute)
            }, endHour, endMinute, true).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val selectedDays = mutableListOf<Int>()
            if (dialogBinding.chipSunday.isChecked) selectedDays.add(1)
            if (dialogBinding.chipMonday.isChecked) selectedDays.add(2)
            if (dialogBinding.chipTuesday.isChecked) selectedDays.add(3)
            if (dialogBinding.chipWednesday.isChecked) selectedDays.add(4)
            if (dialogBinding.chipThursday.isChecked) selectedDays.add(5)
            if (dialogBinding.chipFriday.isChecked) selectedDays.add(6)
            if (dialogBinding.chipSaturday.isChecked) selectedDays.add(7)

            if (selectedDays.isEmpty()) {
                Toast.makeText(this, "Selecciona al menos un día", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isClassTime = dialogBinding.radioClassTime.isChecked
            val scheduleName = if (isClassTime) getString(R.string.class_label) else getString(R.string.recess_label)

            val schedule = Schedule(
                name = scheduleName,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                daysOfWeek = selectedDays,
                isClassTime = isClassTime
            )

            prefs.addSchedule(schedule)
            loadSchedules()
            Toast.makeText(this, getString(R.string.schedule_saved), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    inner class ScheduleAdapter(
        private val onToggle: (Schedule, Boolean) -> Unit,
        private val onDelete: (Schedule) -> Unit
    ) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

        private var schedules = listOf<Schedule>()

        fun submitList(list: List<Schedule>) {
            schedules = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemScheduleBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(schedules[position])
        }

        override fun getItemCount() = schedules.size

        inner class ViewHolder(private val binding: ItemScheduleBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(schedule: Schedule) {
                // Show schedule type with color
                if (schedule.isClassTime) {
                    binding.tvScheduleType.text = "🚫 CLASES"
                    binding.tvScheduleType.setBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(itemView.context, android.R.color.holo_red_light)
                    )
                    binding.tvScheduleType.setTextColor(
                        androidx.core.content.ContextCompat.getColor(itemView.context, android.R.color.white)
                    )
                } else {
                    binding.tvScheduleType.text = "✅ RECREO"
                    binding.tvScheduleType.setBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(itemView.context, android.R.color.holo_green_light)
                    )
                    binding.tvScheduleType.setTextColor(
                        androidx.core.content.ContextCompat.getColor(itemView.context, android.R.color.black)
                    )
                }

                val timeText = String.format(
                    "%02d:%02d - %02d:%02d",
                    schedule.startHour, schedule.startMinute,
                    schedule.endHour, schedule.endMinute
                )
                binding.tvTime.text = timeText

                val dayNames = listOf("D", "L", "M", "X", "J", "V", "S")
                val daysText = schedule.daysOfWeek.map { dayNames[it - 1] }.joinToString(", ")
                binding.tvDays.text = daysText

                binding.switchEnabled.isChecked = schedule.enabled
                binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(schedule, isChecked)
                }

                binding.btnDelete.setOnClickListener {
                    AlertDialog.Builder(itemView.context)
                        .setTitle("Eliminar horario")
                        .setMessage("¿Estás seguro de que deseas eliminar este horario?")
                        .setPositiveButton(R.string.delete) { _, _ ->
                            onDelete(schedule)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }
    }
}

